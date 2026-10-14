package com.example.data

import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import com.example.model.SourceDiagnosticReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MediaApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val diagnosticRunner = SourceDiagnosticRunner(client)

    suspend fun fetchMedia(
        source: MediaSourceConfig,
        query: String = "",
        page: Int = 1
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val request = MediaRequestBuilder.buildFetchRequest(source, query, page)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${response.message}")
                    )
                }

                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@withContext Result.success(emptyList())
                }

                val items = MediaResponseParser.parseMediaItems(source, bodyString)
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun diagnoseSource(source: MediaSourceConfig): SourceDiagnosticReport {
        return diagnosticRunner.diagnoseSource(source)
    }

    internal fun parseMediaItems(source: MediaSourceConfig, body: String): List<MediaItem> =
        MediaResponseParser.parseMediaItems(source, body)

    companion object {
        fun parseRating(ratingStr: String?, isDanbooru: Boolean = false): MediaRating =
            MediaClassifier.parseRating(ratingStr, isDanbooru)

        fun isVideoUrl(url: String?): Boolean =
            MediaClassifier.isVideoUrl(url)

        fun detectMediaType(
            explicitType: String? = null,
            fileExtField: String? = null,
            actualMediaUrl: String? = null,
            rawMediaUrl: String? = null,
            contentType: String? = null,
            tags: List<String> = emptyList()
        ): MediaType = MediaClassifier.detectMediaType(
            explicitType = explicitType,
            fileExtField = fileExtField,
            actualMediaUrl = actualMediaUrl,
            rawMediaUrl = rawMediaUrl,
            contentType = contentType,
            tags = tags
        )
    }
}
