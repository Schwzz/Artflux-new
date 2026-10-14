package com.example.data

import com.example.model.MediaSourceConfig
import org.json.JSONObject

object MediaUrlResolver {

    fun fixUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            trimmed.startsWith("https://") -> trimmed
            trimmed.isNotBlank() && !trimmed.contains("://") -> "https://$trimmed"
            else -> trimmed
        }
    }

    fun resolveUrls(
        source: MediaSourceConfig,
        obj: JSONObject,
        rawImage: String,
        rawThumb: String,
        rawSample: String = ""
    ): Triple<String, String, String?> {
        // Safebooru handling
        if (source.apiUrl.contains("safebooru.org")) {
            val directory = obj.optString("directory")
            val image = obj.optString("image")
            if (directory.isNotBlank() && image.isNotBlank()) {
                val full = "https://safebooru.org/images/$directory/$image"
                val rawPreview = obj.optString("preview_url")
                val thumb = when {
                    rawPreview.isNotBlank() -> fixUrl(rawPreview)
                    image.endsWith(".mp4", true) || image.endsWith(".webm", true) -> {
                        val baseName = image.substringBeforeLast('.')
                        "https://safebooru.org/thumbnails/$directory/thumbnail_$baseName.jpg"
                    }
                    else -> "https://safebooru.org/thumbnails/$directory/thumbnail_$image"
                }
                val sample = if (obj.optBoolean("sample", false)) {
                    "https://safebooru.org/samples/$directory/sample_$image"
                } else null
                return Triple(full, thumb, sample)
            }
        }

        // Danbooru handling
        if (source.apiUrl.contains("danbooru")) {
            val fileUrl = rawImage.ifBlank { obj.optString("file_url").ifBlank { obj.optString("large_file_url") } }
            val sampleUrl = rawSample.ifBlank { obj.optString("large_file_url") }.takeIf { it.isNotBlank() }
            val rawPreview = obj.optString("preview_file_url")
            val previewUrl = when {
                rawPreview.isNotBlank() && !MediaClassifier.isVideoUrl(rawPreview) -> rawPreview
                rawThumb.isNotBlank() && !MediaClassifier.isVideoUrl(rawThumb) -> rawThumb
                sampleUrl != null && !MediaClassifier.isVideoUrl(sampleUrl) -> sampleUrl
                !MediaClassifier.isVideoUrl(fileUrl) -> fileUrl
                else -> ""
            }
            if (fileUrl.isNotBlank()) {
                return Triple(fixUrl(fileUrl), fixUrl(previewUrl), sampleUrl?.let { fixUrl(it) })
            }
        }

        // Yande.re handling
        if (source.apiUrl.contains("yande.re")) {
            val fileUrl = rawImage.ifBlank { obj.optString("file_url").ifBlank { obj.optString("sample_url") } }
            val sampleUrl = rawSample.ifBlank { obj.optString("sample_url") }.takeIf { it.isNotBlank() }
            val rawPreview = obj.optString("preview_url")
            val previewUrl = when {
                rawPreview.isNotBlank() && !MediaClassifier.isVideoUrl(rawPreview) -> rawPreview
                rawThumb.isNotBlank() && !MediaClassifier.isVideoUrl(rawThumb) -> rawThumb
                sampleUrl != null && !MediaClassifier.isVideoUrl(sampleUrl) -> sampleUrl
                !MediaClassifier.isVideoUrl(fileUrl) -> fileUrl
                else -> ""
            }
            if (fileUrl.isNotBlank()) {
                return Triple(fixUrl(fileUrl), fixUrl(previewUrl), sampleUrl?.let { fixUrl(it) })
            }
        }

        // Gelbooru handling (img4.gelbooru.com CDN / HTTPS normalization)
        if (source.apiUrl.contains("gelbooru.com") || source.id.contains("gelbooru")) {
            val directory = obj.optString("directory")
            val image = obj.optString("image")
            val rawFileUrl = rawImage.ifBlank { obj.optString("file_url") }
            val rawThumbUrl = rawThumb.ifBlank { obj.optString("preview_url") }
            val rawSampleUrl = rawSample.ifBlank { obj.optString("sample_url") }

            val full = when {
                rawFileUrl.isNotBlank() -> fixUrl(rawFileUrl)
                directory.isNotBlank() && image.isNotBlank() -> "https://img4.gelbooru.com/images/$directory/$image"
                else -> ""
            }

            val thumb = when {
                rawThumbUrl.isNotBlank() && !MediaClassifier.isVideoUrl(rawThumbUrl) -> fixUrl(rawThumbUrl)
                directory.isNotBlank() && image.isNotBlank() -> {
                    val baseName = image.substringBeforeLast('.')
                    "https://img4.gelbooru.com/thumbnails/$directory/thumbnail_$baseName.jpg"
                }
                !MediaClassifier.isVideoUrl(full) -> full
                else -> ""
            }

            val hasSample = obj.optInt("sample", 0) == 1 || obj.optBoolean("sample", false)
            val sample = when {
                rawSampleUrl.isNotBlank() -> fixUrl(rawSampleUrl)
                hasSample && directory.isNotBlank() && image.isNotBlank() -> "https://img4.gelbooru.com/samples/$directory/sample_$image"
                else -> null
            }

            if (full.isNotBlank()) {
                return Triple(full, thumb, sample)
            }
        }

        // Generic URL resolution consistently respecting configured fields
        val full = fixUrl(rawImage)
        val sample = if (rawSample.isNotBlank()) fixUrl(rawSample) else null

        // Never use a known video URL as a feed image thumbnail.
        // Avoid falling back to the original full-resolution file when a safer preview is available.
        val thumbCandidate = when {
            rawThumb.isNotBlank() && !MediaClassifier.isVideoUrl(rawThumb) -> fixUrl(rawThumb)
            sample != null && !MediaClassifier.isVideoUrl(sample) -> sample
            !MediaClassifier.isVideoUrl(full) -> full
            else -> ""
        }

        return Triple(full, thumbCandidate, sample)
    }
}
