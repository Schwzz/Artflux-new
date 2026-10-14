package com.example.data

import com.example.model.MediaRating
import com.example.model.MediaType

object MediaClassifier {

    fun parseRating(ratingStr: String?, isDanbooru: Boolean = false): MediaRating {
        if (ratingStr.isNullOrBlank()) return MediaRating.UNKNOWN
        val r = ratingStr.lowercase().trim()
        return when {
            r == "false" || r == "sfw" -> MediaRating.SAFE
            r == "true" -> MediaRating.ADULT
            isDanbooru && (r == "g" || r == "general") -> MediaRating.SAFE
            isDanbooru && (r == "s" || r == "sensitive" || r == "q" || r == "questionable") -> MediaRating.SUGGESTIVE
            isDanbooru && (r == "e" || r == "explicit") -> MediaRating.ADULT
            r == "s" && !isDanbooru -> MediaRating.SAFE
            r == "g" || r == "general" || r == "safe" || r == "rating:safe" || r == "rating:g" || r == "rating:general" -> MediaRating.SAFE
            r == "q" || r == "questionable" || r == "sensitive" || r == "suggestive" || r == "rating:questionable" || r == "rating:sensitive" || r == "rating:q" -> MediaRating.SUGGESTIVE
            r == "e" || r == "explicit" || r == "adult" || r == "nsfw" || r == "rating:explicit" || r == "rating:adult" || r == "rating:e" -> MediaRating.ADULT
            r.contains("adult") || r.contains("expl") || r.contains("nsfw") -> MediaRating.ADULT
            r.contains("quest") || r.contains("sensit") || r.contains("suggest") -> MediaRating.SUGGESTIVE
            r.contains("safe") || r.contains("general") -> MediaRating.SAFE
            else -> MediaRating.UNKNOWN
        }
    }

    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore('?').substringBefore('#').lowercase().trim()
        return clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                clean.endsWith(".mkv") || clean.endsWith(".mov") || clean.endsWith(".avi")
    }

    /**
     * Detects the media type using the strongest available evidence in strict priority:
     * 1. Explicit API media type field
     * 2. API file extension field
     * 3. Actual media URL extension
     * 4. HTTP Content-Type as fallback when provided
     * 5. Tags only as a last-resort hint
     */
    fun detectMediaType(
        explicitType: String? = null,
        fileExtField: String? = null,
        actualMediaUrl: String? = null,
        rawMediaUrl: String? = null,
        contentType: String? = null,
        tags: List<String> = emptyList()
    ): MediaType {
        // 1. Explicit API media type / MIME type field
        val type = explicitType?.lowercase()?.trim().orEmpty()
        if (type.isNotBlank()) {
            when {
                type == "gif" || type == "image/gif" || type == "animated_gif" || type.contains("gif") -> return MediaType.GIF
                type.startsWith("video/") || type in listOf("video", "mp4", "webm", "mkv", "mov", "avi") ||
                        type.contains("video") || type.contains("webm") || type.contains("mp4") || type.contains("mkv") -> return MediaType.VIDEO
                type.startsWith("image/") || type in listOf("image", "jpg", "jpeg", "png", "webp", "bmp", "photo", "illustration") -> return MediaType.IMAGE
            }
        }

        // 2. API file extension field
        val ext = fileExtField?.lowercase()?.trim()?.removePrefix(".").orEmpty()
        if (ext.isNotBlank()) {
            when {
                ext == "gif" -> return MediaType.GIF
                ext in listOf("mp4", "webm", "mkv", "mov", "avi") -> return MediaType.VIDEO
                ext in listOf("jpg", "jpeg", "png", "webp", "bmp") -> return MediaType.IMAGE
            }
        }

        // 3. Actual media URL extension (from actual media file, NOT preview/thumbnail)
        fun extractExt(url: String?): String {
            if (url.isNullOrBlank()) return ""
            return url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase().trim()
        }

        val actualExt = extractExt(actualMediaUrl)
        if (actualExt.isNotBlank()) {
            when {
                actualExt == "gif" -> return MediaType.GIF
                actualExt in listOf("mp4", "webm", "mkv", "mov", "avi") -> return MediaType.VIDEO
                actualExt in listOf("jpg", "jpeg", "png", "webp", "bmp") -> return MediaType.IMAGE
            }
        }

        val rawExt = extractExt(rawMediaUrl)
        if (rawExt.isNotBlank()) {
            when {
                rawExt == "gif" -> return MediaType.GIF
                rawExt in listOf("mp4", "webm", "mkv", "mov", "avi") -> return MediaType.VIDEO
                rawExt in listOf("jpg", "jpeg", "png", "webp", "bmp") -> return MediaType.IMAGE
            }
        }

        // 4. HTTP Content-Type as fallback when provided
        val mime = contentType?.lowercase()?.trim().orEmpty()
        if (mime.isNotBlank()) {
            when {
                mime.contains("gif") -> return MediaType.GIF
                mime.startsWith("video/") -> return MediaType.VIDEO
                mime.startsWith("image/") -> return MediaType.IMAGE
            }
        }

        // 5. Tags only as a last-resort hint (without confusing generic 'animated' tags with video)
        val lowerTags = tags.map { it.lowercase().trim() }
        when {
            lowerTags.any { it == "animated_gif" || it == "gif" } -> return MediaType.GIF
            lowerTags.any { it == "webm" || it == "mp4" || it == "video" || it == "mkv" } -> return MediaType.VIDEO
            lowerTags.any { it == "animated" } -> return MediaType.GIF
        }

        return MediaType.IMAGE
    }
}
