package com.example.util

import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType

object QueryBuilder {
    /**
     * Builds an effective search query by combining the user's search text
     * with the active source-specific media query mapping (e.g., "animated" for GIFs)
     * and source-specific rating query mapping (e.g., "rating:e" for Adult).
     *
     * Prevents duplicate tags if already present in the user query.
     */
    fun buildEffectiveQuery(
        userQuery: String,
        source: MediaSourceConfig,
        mediaType: MediaType = MediaType.ALL,
        rating: MediaRating = MediaRating.ALL
    ): String {
        val trimmedUser = userQuery.trim()
        val mediaTag = when (mediaType) {
            MediaType.GIF -> source.gifQueryTag.trim()
            MediaType.VIDEO -> source.videoQueryTag.trim()
            else -> ""
        }

        val ratingTag = when (rating) {
            MediaRating.SAFE -> source.safeRatingTag.trim()
            MediaRating.SUGGESTIVE -> source.suggestiveRatingTag.trim()
            MediaRating.ADULT -> source.adultRatingTag.trim()
            else -> ""
        }

        val tagsToAdd = mutableListOf<String>()
        if (mediaTag.isNotBlank()) tagsToAdd.add(mediaTag)
        if (ratingTag.isNotBlank()) tagsToAdd.add(ratingTag)

        if (tagsToAdd.isEmpty()) {
            return trimmedUser
        }

        if (trimmedUser.isBlank()) {
            return tagsToAdd.joinToString(" ")
        }

        val userTokens = trimmedUser.split("\\s+".toRegex()).map { it.lowercase() }
        val toAppend = mutableListOf<String>()

        for (tag in tagsToAdd) {
            val tagTokens = tag.split("\\s+".toRegex())
            val missing = tagTokens.filter { it.lowercase() !in userTokens }
            if (missing.isNotEmpty()) {
                toAppend.add(missing.joinToString(" "))
            }
        }

        return if (toAppend.isEmpty()) {
            trimmedUser
        } else {
            "$trimmedUser ${toAppend.joinToString(" ")}"
        }
    }
}
