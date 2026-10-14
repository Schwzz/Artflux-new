package com.example.data

import android.content.Context
import com.example.model.MediaSourceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SourceRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE)
    private val credentialVault = SourceCredentialVault(context)
    private val _sources = MutableStateFlow<List<MediaSourceConfig>>(emptyList())
    val sources: StateFlow<List<MediaSourceConfig>> = _sources.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        val savedJson = prefs.getString("custom_sources", null)
        val customSources = mutableListOf<MediaSourceConfig>()
        var needsMigrationResave = false

        if (!savedJson.isNullOrBlank()) {
            try {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawConfig = MediaSourceConfig.fromJson(obj)

                    // Check for legacy credentials stored in plaintext
                    val legacyCreds = SourceCredentials(
                        apiKey = rawConfig.apiKey,
                        authHeaderValue = rawConfig.authHeaderValue,
                        authQueryParams = rawConfig.authQueryParams
                    )

                    val vaultCreds = credentialVault.getCredentials(rawConfig.id)
                    val effectiveCreds = if (legacyCreds.hasCredentials && !vaultCreds.hasCredentials) {
                        // Migrate legacy credentials into secure vault
                        credentialVault.saveCredentials(rawConfig.id, legacyCreds)
                        needsMigrationResave = true
                        legacyCreds
                    } else if (vaultCreds.hasCredentials) {
                        vaultCreds
                    } else {
                        legacyCreds
                    }

                    // Attach secure credentials to configuration instance in-memory
                    val populatedConfig = rawConfig.copy(
                        apiKey = effectiveCreds.apiKey,
                        authHeaderValue = effectiveCreds.authHeaderValue,
                        authQueryParams = effectiveCreds.authQueryParams
                    )
                    customSources.add(populatedConfig)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (needsMigrationResave) {
            // Re-save without plaintext credentials in ordinary SharedPreferences
            saveCustomSourcesInternal(customSources)
        }

        val allSources = MediaSourceConfig.DEFAULT_SOURCES + customSources
        _sources.value = allSources
    }

    fun addSource(source: MediaSourceConfig) {
        // Save sensitive credentials into secure vault
        val creds = SourceCredentials(
            apiKey = source.apiKey,
            authHeaderValue = source.authHeaderValue,
            authQueryParams = source.authQueryParams
        )
        credentialVault.saveCredentials(source.id, creds)

        val currentCustom = _sources.value.filter { !it.isBuiltIn }.toMutableList()
        currentCustom.removeAll { it.id == source.id }
        currentCustom.add(0, source)
        saveCustomSourcesInternal(currentCustom)
    }

    fun updateSource(source: MediaSourceConfig) {
        addSource(source)
    }

    fun deleteSource(sourceId: String) {
        credentialVault.deleteCredentials(sourceId)
        val currentCustom = _sources.value.filter { !it.isBuiltIn && it.id != sourceId }
        saveCustomSourcesInternal(currentCustom)
    }

    private fun saveCustomSourcesInternal(customSources: List<MediaSourceConfig>) {
        val array = JSONArray()
        for (source in customSources) {
            // Store non-sensitive configuration in ordinary SharedPreferences.
            // Sensitive credential values are purged/blanked out from the serialized JSON.
            val nonSensitiveConfig = source.copy(
                apiKey = "",
                authHeaderValue = "",
                authQueryParams = emptyList()
            )
            array.put(nonSensitiveConfig.toJson())
        }
        prefs.edit().putString("custom_sources", array.toString()).apply()
        _sources.value = MediaSourceConfig.DEFAULT_SOURCES + customSources
    }

    fun getSourceById(id: String): MediaSourceConfig? {
        return _sources.value.firstOrNull { it.id == id }
    }
}
