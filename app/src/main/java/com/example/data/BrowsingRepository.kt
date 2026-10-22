package com.example.data

import android.content.Context
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

class BrowsingRepository(context: Context) {
    private val prefs = context.getSharedPreferences("artflux_browsing", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching { BrowsingPreferences.fromJson(JSONObject(prefs.getString("state", "{}")!!)) }.getOrDefault(BrowsingPreferences()))
    val state = mutable.asStateFlow()

    @Synchronized fun update(transform: (BrowsingPreferences) -> BrowsingPreferences) {
        val next = transform(mutable.value)
        if (next == mutable.value) return
        prefs.edit().putString("state", next.toJson().toString()).apply()
        mutable.value = next
    }
    fun block(tag: String) {
        val clean = BrowsingPreferences.normalizeTag(tag).take(200)
        if (clean.isNotEmpty()) update { it.copy(blockedTags = (it.blockedTags.filterNot { t -> t.tag == clean } + BlockedTag(clean)).take(1000)) }
    }
    fun toggleBlock(tag: String) = update { it.copy(blockedTags = it.blockedTags.map { t -> if (t.tag == tag) t.copy(enabled = !t.enabled) else t }) }
    fun removeBlock(tag: String) = update { it.copy(blockedTags = it.blockedTags.filterNot { t -> t.tag == tag }) }
    fun favorite(id: String) = update { it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id) }
    fun rememberQuery(query: String) {
        val clean = query.trim().take(500)
        if (clean.isNotBlank()) update { it.copy(history = (listOf(clean) + it.history.filterNot { q -> q == clean }).take(20)) }
    }
    fun rememberTags(tags: List<String>) = update { it.copy(knownTags = (tags.map { tag -> BrowsingPreferences.normalizeTag(tag) }.filter(String::isNotBlank) + it.knownTags).distinct().take(2000)) }
    fun session(sourceId: String, session: SourceSession) = update { it.copy(activeSourceId = sourceId, sessions = it.sessions + (sourceId to session)) }
    fun saveSearch(sourceId: String, query: String, filters: FilterState) = update { state ->
        val existing = state.savedSearches.any { it.sourceId == sourceId && it.query == query && it.filters == filters }
        if (existing) state else state.copy(savedSearches = (state.savedSearches + SavedSearch(UUID.randomUUID().toString(), query.ifBlank { "Filtered feed" }, sourceId, query, filters)).takeLast(100))
    }
}
