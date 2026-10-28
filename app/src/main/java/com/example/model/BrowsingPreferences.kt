package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class GridDensity(val label: String, val tileWidth: Int) {
    COMPACT("Compact", 120), BALANCED("Balanced", 164), COMFORTABLE("Comfortable", 216)
}

data class BlockedTag(val tag: String, val enabled: Boolean = true)

data class SourceSession(
    val query: String = "", val filters: FilterState = FilterState(),
    val scrollIndex: Int = 0, val scrollOffset: Int = 0
)

data class SavedSearch(
    val id: String, val name: String, val sourceId: String,
    val query: String, val filters: FilterState
)

data class BrowsingPreferences(
    val blockedTags: List<BlockedTag> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val history: List<String> = emptyList(),
    val savedSearches: List<SavedSearch> = emptyList(),
    val knownTags: List<String> = emptyList(),
    val sessions: Map<String, SourceSession> = emptyMap(),
    val activeSourceId: String = MediaSourceConfig.BUILT_IN_SAFEBOORU.id,
    val density: GridDensity = GridDensity.BALANCED
) {
    private val enabledBlockedTags: Set<String> by lazy {
        blockedTags.asSequence().filter { it.enabled }.map { normalizeTag(it.tag) }.toSet()
    }
    fun blocks(item: MediaItem): Boolean = enabledBlockedTags.isNotEmpty() && item.tags.any { normalizeTag(it) in enabledBlockedTags }

    fun suggestions(query: String): List<String> {
        val token = query.substringAfterLast(' ').trim().lowercase(Locale.ROOT)
        if (token.isBlank() || token.startsWith("-")) return emptyList()
        val blocked = enabledBlockedTags
        return knownTags.asSequence().filter { it.startsWith(token, true) && normalizeTag(it) !in blocked }
            .distinct().sortedBy { it.length }.take(8).toList()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("blockedTags", JSONArray().apply { blockedTags.forEach { put(JSONObject().put("tag", it.tag).put("enabled", it.enabled)) } })
        put("favorites", JSONArray(favorites.toList())); put("history", JSONArray(history))
        put("knownTags", JSONArray(knownTags)); put("density", density.name); put("activeSourceId", activeSourceId)
        put("sessions", JSONObject().apply { sessions.forEach { (id, s) -> put(id, JSONObject().put("query", s.query)
            .put("filters", s.filters.toJson()).put("index", s.scrollIndex).put("offset", s.scrollOffset)) } })
        put("savedSearches", JSONArray().apply { savedSearches.forEach { put(JSONObject().put("id", it.id).put("name", it.name)
            .put("sourceId", it.sourceId).put("query", it.query).put("filters", it.filters.toJson())) } })
    }

    companion object {
        fun normalizeTag(tag: String): String = tag.trim().lowercase(Locale.ROOT)
        fun fromJson(json: JSONObject): BrowsingPreferences {
            fun strings(key: String, max: Int) = json.optJSONArray(key)?.let { arr ->
                (0 until minOf(arr.length(), max)).mapNotNull { arr.optString(it).trim().takeIf(String::isNotEmpty) }.distinct()
            }.orEmpty()
            val blocked = json.optJSONArray("blockedTags")?.let { arr -> (0 until minOf(arr.length(), 1000)).mapNotNull {
                arr.optJSONObject(it)?.let { o -> normalizeTag(o.optString("tag")).takeIf(String::isNotBlank)?.let { BlockedTag(it, o.optBoolean("enabled", true)) } }
            } }.orEmpty().distinctBy { it.tag }
            val sessions = mutableMapOf<String, SourceSession>()
            json.optJSONObject("sessions")?.let { obj -> obj.keys().asSequence().take(200).forEach { key ->
                obj.optJSONObject(key)?.let { o -> sessions[key] = SourceSession(o.optString("query").take(500), filterFromJson(o.optJSONObject("filters")),
                    o.optInt("index").coerceIn(0, 100000), o.optInt("offset").coerceIn(0, 100000)) }
            } }
            val searches = json.optJSONArray("savedSearches")?.let { a -> (0 until minOf(a.length(), 100)).mapNotNull {
                a.optJSONObject(it)?.let { o -> SavedSearch(o.getString("id"), o.getString("name").take(100), o.getString("sourceId"),
                    o.optString("query").take(500), filterFromJson(o.optJSONObject("filters"))) }
            } }.orEmpty().distinctBy { it.id }
            return BrowsingPreferences(blocked, strings("favorites", 200).toSet(), strings("history", 20), searches,
                strings("knownTags", 2000), sessions, json.optString("activeSourceId", MediaSourceConfig.BUILT_IN_SAFEBOORU.id),
                runCatching { GridDensity.valueOf(json.optString("density")) }.getOrDefault(GridDensity.BALANCED))
        }
    }
}

fun FilterState.toJson(): JSONObject = JSONObject().put("sort", sort.name).put("rating", rating.name)
    .put("orientation", orientation.name).put("mediaType", mediaType.name)

fun filterFromJson(json: JSONObject?): FilterState = FilterState(
    sort = runCatching { SortOption.valueOf(json?.optString("sort").orEmpty()) }.getOrDefault(SortOption.LATEST),
    rating = runCatching { MediaRating.valueOf(json?.optString("rating").orEmpty()) }.getOrDefault(MediaRating.ALL),
    orientation = runCatching { Orientation.valueOf(json?.optString("orientation").orEmpty()) }.getOrDefault(Orientation.ALL),
    mediaType = runCatching { MediaType.valueOf(json?.optString("mediaType").orEmpty()) }.getOrDefault(MediaType.ALL)
)
