package com.example.data

import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import com.example.model.SourceDiagnosticReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MediaApiClient internal constructor(private val client: OkHttpClient) : MediaFetcher {
    constructor() : this(OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build())

    private val diagnosticRunner = SourceDiagnosticRunner(client)

    override suspend fun fetchMedia(
        source: MediaSourceConfig,
        query: String,
        page: Int
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val request = MediaRequestBuilder.buildFetchRequest(source, query, page)
            val response = client.newCall(request).awaitBody()
            if (!response.isSuccessful) {
                return@withContext Result.failure(SourceRequestException.http(response.code, response.retryAfter))
            }

            val bodyString = response.bodyString
            if (bodyString.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            val authError = SourceDiagnosticRunner.detectAuthError(bodyString)
            if (authError != null) {
                return@withContext Result.failure(SourceRequestException(com.example.model.SourceCondition.AUTH_REQUIRED))
            }

            val items = MediaResponseParser.parseMediaItems(source, bodyString, strict = true)
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(SourceRequestException.from(e))
        }
    }

    override suspend fun diagnoseSource(source: MediaSourceConfig): SourceDiagnosticReport {
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
