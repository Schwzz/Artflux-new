package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.model.AuthParam
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SourceCredentials(
    val apiKey: String = "",
    val authHeaderValue: String = "",
    val authQueryParams: List<AuthParam> = emptyList()
) {
    val hasCredentials: Boolean
        get() = apiKey.isNotBlank() || authHeaderValue.isNotBlank() || authQueryParams.isNotEmpty()
}

/**
 * Android-appropriate secure storage for sensitive custom source credentials
 * (API keys, Bearer tokens, custom header values, and authentication query parameters).
 *
 * Keeps credentials isolated from non-sensitive source configuration, encrypting values
 * using AES-GCM and storing them in private storage.
 */
class SourceCredentialVault internal constructor(
    context: Context,
    private val keyProvider: (() -> SecretKey)?
) {
    constructor(context: Context) : this(context, null)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy { keyProvider?.invoke() ?: getOrCreateSecretKey() }

    fun saveCredentials(sourceId: String, credentials: SourceCredentials): Boolean {
        if (!credentials.hasCredentials) {
            return deleteCredentials(sourceId)
        }

        return try {
            val json = JSONObject().apply {
                put("apiKey", credentials.apiKey)
                put("authHeaderValue", credentials.authHeaderValue)
                val paramsArray = JSONArray()
                for (param in credentials.authQueryParams) {
                    paramsArray.put(param.toJson())
                }
                put("authQueryParams", paramsArray)
            }

            val encrypted = encrypt(json.toString())
            prefs.edit().putString(KEY_PREFIX + sourceId, encrypted).commit()
        } catch (e: Exception) {
            // Never fall back to plaintext or Base64 encoding.
            // On failure, return false and do not write insecure data.
            false
        }
    }

    fun getCredentials(sourceId: String): SourceCredentials {
        val encrypted = prefs.getString(KEY_PREFIX + sourceId, null) ?: return SourceCredentials()

        return try {
            val decryptedJson = decrypt(encrypted)
            val json = JSONObject(decryptedJson)
            val apiKey = json.optString("apiKey", "")
            val authHeaderValue = json.optString("authHeaderValue", "")
            val paramsList = mutableListOf<AuthParam>()
            val paramsArray = json.optJSONArray("authQueryParams")
            if (paramsArray != null) {
                for (i in 0 until paramsArray.length()) {
                    val pObj = paramsArray.optJSONObject(i)
                    if (pObj != null) {
                        paramsList.add(AuthParam.fromJson(pObj))
                    }
                }
            }
            SourceCredentials(apiKey, authHeaderValue, paramsList)
        } catch (e: Exception) {
            // Decryption failure (corrupt or invalid ciphertext) - do not attempt insecure decoding
            SourceCredentials()
        }
    }

    fun deleteCredentials(sourceId: String): Boolean =
        prefs.edit().remove(KEY_PREFIX + sourceId).commit()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Combine IV (12 bytes for GCM) + ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted payload length")
        }
        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainTextBytes = cipher.doFinal(cipherText)
        return String(plainTextBytes, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: generateKeyStoreKey()
    }

    private fun generateKeyStoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "artflux_secure_credentials"
        private const val KEY_PREFIX = "cred_"
        private const val KEYSTORE_ALIAS = "artflux_source_credentials_key"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
