package com.example.data

import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import org.json.JSONArray
import org.json.JSONObject

object MediaResponseParser {

    fun parseMediaItems(source: MediaSourceConfig, body: String): List<MediaItem> {
        val trimmed = body.trim()
        val itemsArray: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val rootObj = JSONObject(trimmed)
            if (source.itemsPath.isBlank()) {
                when {
                    rootObj.has("post") -> rootObj.optJSONArray("post")
                    rootObj.has("posts") -> rootObj.optJSONArray("posts")
                    rootObj.has("data") -> rootObj.optJSONArray("data")
                    rootObj.has("results") -> rootObj.optJSONArray("results")
                    rootObj.has("images") -> rootObj.optJSONArray("images")
                    else -> null
                } ?: JSONArray()
            } else {
                JsonPathExtractor.extractNestedJsonArray(rootObj, source.itemsPath) ?: JSONArray()
            }
        }

        val result = mutableListOf<MediaItem>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            val item = mapJsonToMediaItem(source, obj, i)
            if (item != null) {
                result.add(item)
            }
        }
        return result
    }

    fun mapJsonToMediaItem(source: MediaSourceConfig, obj: JSONObject, index: Int): MediaItem? {
        val rawImage = JsonPathExtractor.extractValue(obj, source.imageUrlField)
        val rawThumb = if (source.thumbUrlField.isNotBlank()) JsonPathExtractor.extractValue(obj, source.thumbUrlField) else ""
        val rawSample = if (source.sampleUrlField.isNotBlank()) JsonPathExtractor.extractValue(obj, source.sampleUrlField) else ""

        // Determine image, thumbnail, and sample URLs consistently respecting configured fields
        val (finalImage, finalThumb, finalSample) = MediaUrlResolver.resolveUrls(source, obj, rawImage, rawThumb, rawSample)
        if (finalImage.isBlank()) return null

        val id = JsonPathExtractor.extractValue(obj, "id").ifBlank {
            JsonPathExtractor.extractValue(obj, "image_id").ifBlank {
                JsonPathExtractor.extractValue(obj, "signature").ifBlank { "item-${source.id}-$index" }
            }
        }
        val title = JsonPathExtractor.extractValue(obj, source.titleField).ifBlank {
            JsonPathExtractor.extractValue(obj, "signature").ifBlank {
                JsonPathExtractor.extractValue(obj, "name").ifBlank { "Art #$id" }
            }
        }

        val postUrl = when {
            source.apiUrl.contains("safebooru.org") -> "https://safebooru.org/index.php?page=post&s=view&id=$id"
            source.apiUrl.contains("danbooru") -> "https://danbooru.donmai.us/posts/$id"
            source.apiUrl.contains("yande.re") -> "https://yande.re/post/show/$id"
            source.apiUrl.contains("gelbooru.com") -> "https://gelbooru.com/index.php?page=post&s=view&id=$id"
            source.postUrlField.isNotBlank() -> {
                val rawPost = JsonPathExtractor.extractValue(obj, source.postUrlField)
                if (rawPost.isNotBlank() && (rawPost.contains("://") || rawPost.startsWith("//") || rawPost.startsWith("/"))) {
                    MediaUrlResolver.fixUrl(rawPost)
                } else null
            }
            else -> null
        }

        // Tags parsing
        val tagsList = JsonPathExtractor.extractTags(obj, source.tagsField)

        // Rating parsing
        val isDanbooruSource = source.apiUrl.contains("danbooru") || source.id.contains("danbooru")
        val isWaifuSource = source.apiUrl.contains("waifu.im") || source.id.contains("waifu")

        val rating = if (isWaifuSource) {
            when {
                obj.has("isNsfw") -> if (obj.optBoolean("isNsfw")) MediaRating.ADULT else MediaRating.SAFE
                obj.has("is_nsfw") -> if (obj.optBoolean("is_nsfw")) MediaRating.ADULT else MediaRating.SAFE
                else -> MediaClassifier.parseRating(JsonPathExtractor.extractValue(obj, source.ratingField), isDanbooru = false)
            }
        } else {
            MediaClassifier.parseRating(
                ratingStr = JsonPathExtractor.extractValue(obj, source.ratingField),
                isDanbooru = isDanbooruSource
            )
        }

        // Media type detection (GIF, VIDEO, IMAGE) using strong evidence hierarchy
        val explicitTypeField = JsonPathExtractor.extractValue(obj, source.mediaTypeField).ifBlank {
            obj.optString("media_type", obj.optString("type", obj.optString("file_type", obj.optString("mime_type", ""))))
        }
        val fileExtField = obj.optString("file_ext", obj.optString("ext", obj.optString("extension", "")))
        val safebooruImage = obj.optString("image")
        val effectiveFileExt = fileExtField.ifBlank {
            if (safebooruImage.isNotBlank()) safebooruImage.substringAfterLast('.', "") else ""
        }

        val mediaType = MediaClassifier.detectMediaType(
            explicitType = explicitTypeField,
            fileExtField = effectiveFileExt,
            actualMediaUrl = finalImage,
            rawMediaUrl = rawImage,
            tags = tagsList
        )

        val resolvedFileExt = effectiveFileExt.ifBlank {
            finalImage.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() }
        }

        val rawAuthor = JsonPathExtractor.extractValue(obj, source.authorField)
        val author = if (rawAuthor.isNotBlank() && !rawAuthor.startsWith("[")) rawAuthor else {
            val artistsArr = obj.optJSONArray("artists") ?: obj.optJSONArray(source.authorField)
            if (artistsArr != null && artistsArr.length() > 0) {
                (0 until artistsArr.length()).mapNotNull { idx ->
                    val aObj = artistsArr.optJSONObject(idx)
                    aObj?.optString("name") ?: aObj?.optString("artist")
                }.filter { it.isNotBlank() }.joinToString(", ")
            } else if (rawAuthor.isNotBlank()) rawAuthor else null
        }
        val width = obj.optInt("width", 0).takeIf { it > 0 } ?: obj.optInt("image_width", 0).takeIf { it > 0 }
        val height = obj.optInt("height", 0).takeIf { it > 0 } ?: obj.optInt("image_height", 0).takeIf { it > 0 }
        val fileSize = obj.optLong("file_size", 0L).takeIf { it > 0 }

        // Metadata extraction for sorting accuracy
        val score = obj.optInt("score", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?: obj.optInt("up_score", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        val favorites = obj.optInt("fav_count", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            ?: obj.optInt("favorites", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        val views = obj.optInt("views", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

        val staticPreview = when (mediaType) {
            MediaType.VIDEO -> when {
                finalThumb.isNotBlank() && !MediaClassifier.isVideoUrl(finalThumb) -> finalThumb
                !finalSample.isNullOrBlank() && !MediaClassifier.isVideoUrl(finalSample) -> finalSample
                else -> ""
            }
            MediaType.GIF -> when {
                finalThumb.isNotBlank() && !MediaClassifier.isVideoUrl(finalThumb) -> finalThumb
                !finalSample.isNullOrBlank() && !MediaClassifier.isVideoUrl(finalSample) -> finalSample
                !MediaClassifier.isVideoUrl(finalImage) -> finalImage
                else -> ""
            }
            MediaType.IMAGE, MediaType.ALL -> when {
                finalThumb.isNotBlank() && !MediaClassifier.isVideoUrl(finalThumb) -> finalThumb
                !finalSample.isNullOrBlank() && !MediaClassifier.isVideoUrl(finalSample) -> finalSample
                !MediaClassifier.isVideoUrl(finalImage) -> finalImage
                else -> ""
            }
        }

        return MediaItem(
            id = id,
            title = title,
            actualMediaUrl = finalImage,
            previewUrl = staticPreview,
            sampleUrl = finalSample,
            postUrl = postUrl,
            tags = tagsList,
            rating = rating,
            mediaType = mediaType,
            width = width,
            height = height,
            author = author,
            sourceName = source.name,
            description = obj.optString("alt_text", obj.optString("description", "")).ifBlank { null },
            fileSize = fileSize,
            fileExt = resolvedFileExt,
            score = score,
            favorites = favorites,
            views = views
        )
    }
}
