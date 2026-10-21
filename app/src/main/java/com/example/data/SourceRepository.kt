package com.example.data

import android.content.Context
import com.example.model.MediaSourceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SourceRepository internal constructor(
    context: Context,
    private val credentialVault: SourceCredentialVault
) {
    constructor(context: Context) : this(context, SourceCredentialVault(context))
    private val prefs = context.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE)
    private val _sources = MutableStateFlow<List<MediaSourceConfig>>(emptyList())
    val sources: StateFlow<List<MediaSourceConfig>> = _sources.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        val savedJson = prefs.getString("custom_sources", null)
        val customSources = mutableListOf<MediaSourceConfig>()
        var needsMigrationResave = false
        var migrationFailed = false

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
                        if (credentialVault.saveCredentials(rawConfig.id, legacyCreds)) {
                            needsMigrationResave = true
                        } else {
                            migrationFailed = true
                        }
                        legacyCreds
                    } else if (vaultCreds.hasCredentials) {
                        if (legacyCreds.hasCredentials) needsMigrationResave = true
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

        if (needsMigrationResave && !migrationFailed) {
            // Re-save without plaintext credentials in ordinary SharedPreferences
            saveCustomSourcesInternal(customSources)
        }

        val allSources = MediaSourceConfig.DEFAULT_SOURCES + customSources
        _sources.value = allSources
    }

    fun addSource(source: MediaSourceConfig): Boolean {
        val currentCustom = _sources.value.filter { !it.isBuiltIn }.toMutableList()
        currentCustom.removeAll { it.id == source.id }
        currentCustom.add(0, source)
        return saveCustomSourcesInternal(currentCustom)
    }

    fun updateSource(source: MediaSourceConfig): Boolean = addSource(source)

    fun deleteSource(sourceId: String): Boolean {
        val currentCustom = _sources.value.filter { !it.isBuiltIn && it.id != sourceId }
        if (!saveCustomSourcesInternal(currentCustom)) return false
        credentialVault.deleteCredentials(sourceId)
        return true
    }

    private fun saveCustomSourcesInternal(customSources: List<MediaSourceConfig>): Boolean {
        // Verify every credential before removing plaintext from the complete source list.
        // This also protects a pending legacy migration when another source is edited.
        for (source in customSources) {
            val credentials = SourceCredentials(source.apiKey, source.authHeaderValue, source.authQueryParams)
            if (!credentialVault.saveCredentials(source.id, credentials)) return false
        }
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
        if (!prefs.edit().putString("custom_sources", array.toString()).commit()) return false
        _sources.value = MediaSourceConfig.DEFAULT_SOURCES + customSources
        return true
    }

    fun getSourceById(id: String): MediaSourceConfig? {
        return _sources.value.firstOrNull { it.id == id }
    }
}
