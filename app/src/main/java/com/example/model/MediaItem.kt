package com.example.model

import org.json.JSONArray
import org.json.JSONObject

enum class MediaRating(val label: String) {
    ALL("All Ratings"),
    SAFE("Safe"),
    SUGGESTIVE("Suggestive"),
    ADULT("Adult"),
    UNKNOWN("Unknown");

    companion object {
        val QUESTIONABLE: MediaRating get() = SUGGESTIVE
        val EXPLICIT: MediaRating get() = ADULT
    }
}

enum class MediaType(val label: String) {
    ALL("All Types"),
    IMAGE("Image"),
    GIF("GIF"),
    VIDEO("Video")
}

enum class Orientation(val label: String) {
    ALL("Any"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    SQUARE("Square")
}

enum class SortOption(val label: String) {
    LATEST("Latest"),
    POPULAR("Popular"),
    TOP_RATED("Top Rated"),
    RANDOM("Random")
}

enum class ThumbnailQuality(val label: String, val resolution: Int) {
    Q360("360p", 360),
    Q480("480p", 480),
    Q720("720p", 720),
    Q1080("1080p", 1080);

    companion object {
        val DEFAULT = Q720
    }
}

enum class DownloadQuality(val label: String, val description: String) {
    Q360("360p", "Preview resolution (~360p)"),
    Q480("480p", "Standard resolution (~480p)"),
    Q720("720p", "HD sample (~720p)"),
    Q1080("1080p", "Full HD sample (~1080p)"),
    ORIGINAL("Original", "Full original resolution");

    companion object {
        val DEFAULT = ORIGINAL
    }
}

enum class AppTheme(val label: String) {
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        val DEFAULT = DARK
    }
}

enum class ColorPalette(val label: String, val subtitle: String) {
    ARCTIC_SIGNAL("Arctic Signal", "Electric Cyan, Violet & Coral"),
    EMERALD_NOIR("Emerald Noir", "Emerald, Sapphire & Gold"),
    CRIMSON_FLUX("Crimson Flux", "Crimson, Electric Blue & Amber");

    companion object {
        val DEFAULT = ARCTIC_SIGNAL
    }
}

data class FilterState(
    val sort: SortOption = SortOption.LATEST,
    val rating: MediaRating = MediaRating.ALL,
    val orientation: Orientation = Orientation.ALL,
    val mediaType: MediaType = MediaType.ALL
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (sort != SortOption.LATEST) count++
            if (rating != MediaRating.ALL) count++
            if (orientation != Orientation.ALL) count++
            if (mediaType != MediaType.ALL) count++
            return count
        }
}

data class MediaItem(
    val id: String,
    val title: String,
    val actualMediaUrl: String,
    val previewUrl: String,
    val sampleUrl: String? = null,
    val postUrl: String? = null,
    val tags: List<String> = emptyList(),
    val rating: MediaRating = MediaRating.SAFE,
    val mediaType: MediaType = MediaType.IMAGE,
    val width: Int? = null,
    val height: Int? = null,
    val author: String? = null,
    val sourceName: String = "",
    val description: String? = null,
    val fileSize: Long? = null,
    val fileExt: String? = null,
    val score: Int? = null,
    val favorites: Int? = null,
    val views: Int? = null
) {
    // Backward compatibility accessors for existing consumers
    val imageUrl: String get() = actualMediaUrl
    val thumbnailUrl: String get() = previewUrl

    val aspectRatio: Float
        get() {
            return if (width != null && height != null && height > 0) {
                (width.toFloat() / height.toFloat()).coerceIn(0.6f, 1.8f)
            } else {
                1.0f
            }
        }

    private fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore('?').substringBefore('#').lowercase().trim()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                clean.endsWith(".mkv") || clean.endsWith(".mov") || clean.endsWith(".avi")
    }

    /**
     * Deterministic feed thumbnail resolution:
     * - VIDEO: Always static preview image; never a video file.
     * - GIF: Always static preview image; avoids heavy animated downloads on feed grid.
     * - IMAGE: Preview image (or sample if higher quality chosen and valid).
     */
    fun getThumbnailForQuality(quality: ThumbnailQuality): String {
        // Video items must NEVER load a video URL as an image in the feed. Use static preview/poster.
        if (mediaType == MediaType.VIDEO) {
            return if (!isVideoUrl(previewUrl)) {
                if (quality in listOf(ThumbnailQuality.Q720, ThumbnailQuality.Q1080) && !sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl)) {
                    sampleUrl
                } else {
                    previewUrl
                }
            } else ""
        }

        // GIF items: feed uses lightweight static preview image to keep scrolling fast and fluid
        if (mediaType == MediaType.GIF) {
            return if (quality in listOf(ThumbnailQuality.Q720, ThumbnailQuality.Q1080) && !sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl)) {
                sampleUrl
            } else if (!isVideoUrl(previewUrl)) {
                previewUrl
            } else ""
        }

        // Image items: respect 360p/480p/720p/1080p quality preference while avoiding large/original files when preview exists
        return when (quality) {
            ThumbnailQuality.Q360, ThumbnailQuality.Q480 -> {
                if (previewUrl.isNotBlank() && !isVideoUrl(previewUrl)) {
                    previewUrl
                } else if (!sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl)) {
                    sampleUrl
                } else if (!isVideoUrl(actualMediaUrl)) {
                    actualMediaUrl
                } else ""
            }
            ThumbnailQuality.Q720, ThumbnailQuality.Q1080 -> {
                if (!sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl)) {
                    sampleUrl
                } else if (previewUrl.isNotBlank() && !isVideoUrl(previewUrl)) {
                    previewUrl
                } else if (!isVideoUrl(actualMediaUrl)) {
                    actualMediaUrl
                } else ""
            }
        }
    }

    fun getThumbnailFallbackUrl(primaryQuality: ThumbnailQuality): String {
        val primary = getThumbnailForQuality(primaryQuality)
        return when {
            primary == previewUrl && !sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl) -> sampleUrl
            primary == sampleUrl && previewUrl.isNotBlank() && !isVideoUrl(previewUrl) -> previewUrl
            previewUrl.isNotBlank() && !isVideoUrl(previewUrl) -> previewUrl
            !sampleUrl.isNullOrBlank() && !isVideoUrl(sampleUrl) -> sampleUrl
            mediaType != MediaType.VIDEO && !isVideoUrl(actualMediaUrl) -> actualMediaUrl
            else -> primary
        }
    }

    fun getDownloadUrl(quality: DownloadQuality): String {
        // For animated GIFs and video files, always use the real original media URL
        if (mediaType == MediaType.GIF || mediaType == MediaType.VIDEO) {
            return actualMediaUrl
        }

        return when (quality) {
            DownloadQuality.ORIGINAL -> actualMediaUrl
            DownloadQuality.Q1080 -> sampleUrl?.ifBlank { null } ?: actualMediaUrl
            DownloadQuality.Q720 -> sampleUrl?.ifBlank { null } ?: actualMediaUrl
            DownloadQuality.Q480 -> previewUrl.ifBlank { sampleUrl ?: actualMediaUrl }
            DownloadQuality.Q360 -> previewUrl.ifBlank { sampleUrl ?: actualMediaUrl }
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("actualMediaUrl", actualMediaUrl)
        obj.put("previewUrl", previewUrl)
        if (sampleUrl != null) obj.put("sampleUrl", sampleUrl)
        if (postUrl != null) obj.put("postUrl", postUrl)
        val tagsArray = JSONArray()
        for (t in tags) {
            tagsArray.put(t)
        }
        obj.put("tags", tagsArray)
        obj.put("rating", rating.name)
        obj.put("mediaType", mediaType.name)
        if (width != null) obj.put("width", width)
        if (height != null) obj.put("height", height)
        if (author != null) obj.put("author", author)
        obj.put("sourceName", sourceName)
        if (description != null) obj.put("description", description)
        if (fileSize != null) obj.put("fileSize", fileSize)
        if (fileExt != null) obj.put("fileExt", fileExt)
        if (score != null) obj.put("score", score)
        if (favorites != null) obj.put("favorites", favorites)
        if (views != null) obj.put("views", views)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): MediaItem {
            val tagsList = mutableListOf<String>()
            val tagsArray = obj.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
            }
            val ratingStr = obj.optString("rating", MediaRating.SAFE.name)
            val ratingVal = try { MediaRating.valueOf(ratingStr) } catch (e: Exception) { MediaRating.SAFE }

            val typeStr = obj.optString("mediaType", MediaType.IMAGE.name)
            val typeVal = try { MediaType.valueOf(typeStr) } catch (e: Exception) { MediaType.IMAGE }

            return MediaItem(
                id = obj.getString("id"),
                title = obj.optString("title", "Untitled"),
                actualMediaUrl = obj.optString("actualMediaUrl", obj.optString("imageUrl", "")),
                previewUrl = obj.optString("previewUrl", obj.optString("thumbnailUrl", "")),
                sampleUrl = obj.optString("sampleUrl").ifBlank { null },
                postUrl = obj.optString("postUrl").ifBlank { null },
                tags = tagsList,
                rating = ratingVal,
                mediaType = typeVal,
                width = if (obj.has("width")) obj.getInt("width") else null,
                height = if (obj.has("height")) obj.getInt("height") else null,
                author = obj.optString("author").ifBlank { null },
                sourceName = obj.optString("sourceName", ""),
                description = obj.optString("description").ifBlank { null },
                fileSize = if (obj.has("fileSize")) obj.getLong("fileSize") else null,
                fileExt = obj.optString("fileExt").ifBlank { null },
                score = if (obj.has("score")) obj.getInt("score") else null,
                favorites = if (obj.has("favorites")) obj.getInt("favorites") else null,
                views = if (obj.has("views")) obj.getInt("views") else null
            )
        }
    }
}
