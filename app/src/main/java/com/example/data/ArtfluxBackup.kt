package com.example.data

import com.example.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class ArtfluxBackup(
    val items: List<MediaItem>, val collections: List<ArtCollection>,
    val sources: List<MediaSourceConfig>, val browsing: BrowsingPreferences,
    val palette: ColorPalette, val thumbnail: ThumbnailQuality, val blur: Boolean, val loop: Boolean
)

object ArtfluxBackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024

    fun encode(backup: ArtfluxBackup): String = JSONObject().apply {
        put("format", "artflux-backup"); put("version", 1)
        put("items", JSONArray().apply { backup.items.forEach { put(it.toJson()) } })
        put("collections", JSONArray().apply { backup.collections.forEach { put(it.toJson()) } })
        put("sources", JSONArray().apply { backup.sources.filterNot { it.isBuiltIn }.forEach { put(withoutSecrets(it).toJson()) } })
        put("browsing", backup.browsing.toJson())
        put("settings", JSONObject().put("palette", backup.palette.name).put("thumbnail", backup.thumbnail.name)
            .put("blur", backup.blur).put("loop", backup.loop))
    }.toString(2)

    fun decode(text: String): ArtfluxBackup {
        require(text.toByteArray().size <= MAX_BYTES) { "Backup exceeds 8 MB" }
        val json = JSONObject(text)
        require(json.optString("format") == "artflux-backup" && json.optInt("version") == 1) { "Unsupported Artflux backup" }
        fun array(key: String, max: Int): JSONArray = json.getJSONArray(key).also { require(it.length() <= max) { "Too many $key" } }
        val a = array("items", 10000)
        val items = (0 until a.length()).map { MediaItem.fromJson(a.getJSONObject(it)) }.onEach {
            require(it.id.isNotBlank() && it.actualMediaUrl.toHttpUrlOrNull() != null) { "Invalid artwork in backup" }
        }.distinctBy { it.libraryKey }
        val c = array("collections", 1000)
        val collections = (0 until c.length()).map { ArtCollection.fromJson(c.getJSONObject(it)) }.onEach {
            require(it.id.isNotBlank() && it.name.isNotBlank()) { "Invalid collection" }
        }
        val s = array("sources", 100)
        val sources = (0 until s.length()).map { withoutSecrets(MediaSourceConfig.fromJson(s.getJSONObject(it))).copy(isBuiltIn = false) }.onEach {
            require(it.apiUrl.toHttpUrlOrNull() != null && it.name.isNotBlank() && it.id.isNotBlank()) { "Invalid source" }
        }
        val prefs = json.getJSONObject("settings")
        return ArtfluxBackup(items, collections, sources, BrowsingPreferences.fromJson(json.getJSONObject("browsing")),
            ColorPalette.valueOf(prefs.getString("palette")), ThumbnailQuality.valueOf(prefs.getString("thumbnail")),
            prefs.getBoolean("blur"), prefs.getBoolean("loop"))
    }

    fun withoutSecrets(source: MediaSourceConfig): MediaSourceConfig {
        val sensitive = source.getEffectiveAuthQueryParams().keys.map { it.lowercase() }.toSet() +
            setOf("api_key", "apikey", "key", "token", "access_token", "password", "authorization", "user_id")
        val url = source.apiUrl.toHttpUrlOrNull()
        val safe = url?.newBuilder()?.username("")?.password("")?.apply {
            url.queryParameterNames.filter { it.lowercase() in sensitive }.forEach { removeAllQueryParameters(it) }
        }?.build()?.toString() ?: ""
        return source.copy(apiUrl = safe, apiKey = "", authHeaderValue = "", authQueryParams = emptyList())
    }
}
