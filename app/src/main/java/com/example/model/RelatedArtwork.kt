package com.example.model

import java.util.Locale

data class RelatedArtworkState(
    val seed: MediaItem,
    val source: MediaSourceConfig?,
    val tags: List<String>,
    val selectedTag: String?,
    val filters: FilterState,
    val localItems: List<MediaItem> = emptyList(),
    val remoteItems: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val nextPage: Int = 1,
    val reachedEnd: Boolean = false,
    val open: Boolean = true,
    val artistOnly: Boolean = false
)

/** Related means shared metadata, rather than visual/AI similarity. */
object RelatedArtwork {
    private val generic = setOf("solo", "1girl", "1boy", "highres", "absurdres", "commentary", "translated", "rating:safe")
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
    fun artist(item: MediaItem): String? = item.author?.trim()?.takeIf {
        it.any(Char::isLetter) && normalize(it) !in setOf("unknown", "anonymous")
    }
    fun sameArtist(seed: MediaItem, candidate: MediaItem): Boolean = artist(seed)?.let { author ->
        artist(candidate)?.equals(author, ignoreCase = true) == true
    } == true
    fun tags(item: MediaItem, browsing: BrowsingPreferences): List<String> = item.tags.map { it.trim() }
        .filter { it.isNotBlank() && it.length <= 100 && !it.startsWith('-') && it.matches(Regex("[\\p{L}\\p{N}_()'./-]+")) }
        .filterNot { tag -> browsing.blockedTags.any { it.enabled && normalize(it.tag) == normalize(tag) } }
        .distinctBy(::normalize).sortedBy { normalize(it) in generic }.take(30)

    fun rank(seed: MediaItem, candidates: List<MediaItem>, browsing: BrowsingPreferences, tag: String? = null): List<MediaItem> {
        val seedTags = seed.tags.map(::normalize).toSet()
        return candidates.distinctBy { it.libraryKey }.distinctBy { it.actualMediaUrl.ifBlank { it.libraryKey } }.filterNot {
            it.libraryKey == seed.libraryKey || it.actualMediaUrl == seed.actualMediaUrl || browsing.blocks(it)
        }.mapNotNull { candidate ->
            val shared = candidate.tags.map(::normalize).toSet().intersect(seedTags)
            val sameAuthor = sameArtist(seed, candidate)
            if (tag != null && candidate.tags.none { normalize(it) == normalize(tag) }) null
            else if (shared.isEmpty() && !sameAuthor) null
            else candidate to (shared.sumOf { if (it in generic) 1 else 3 } + if (sameAuthor) 4 else 0)
        }.sortedByDescending { it.second }.map { it.first }
    }
}
