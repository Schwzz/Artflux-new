package com.example.data

import com.example.model.MediaSourceConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

object MediaRequestBuilder {

    fun buildFetchRequest(
        source: MediaSourceConfig,
        query: String = "",
        page: Int = 1
    ): Request {
        val urlBuilder = source.apiUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid API URL: ${source.apiUrl}")

        val isWaifu = source.apiUrl.contains("waifu.im") || source.id.contains("waifu")

        if (isWaifu) {
            // Waifu.im API v7
            val actualPage = (page - 1) + source.pageStartsAt
            urlBuilder.addQueryParameter("page", actualPage.toString())
            urlBuilder.addQueryParameter("pageSize", source.defaultPageSize.toString())

            var cleanQuery = query.trim()
            if (cleanQuery.contains("is_nsfw:false", ignoreCase = true)) {
                cleanQuery = cleanQuery.replace("is_nsfw:false", "", ignoreCase = true).trim()
                urlBuilder.addQueryParameter("is_nsfw", "false")
            } else if (cleanQuery.contains("is_nsfw:true", ignoreCase = true)) {
                cleanQuery = cleanQuery.replace("is_nsfw:true", "", ignoreCase = true).trim()
                urlBuilder.addQueryParameter("is_nsfw", "true")
            }

            if (cleanQuery.isNotBlank()) {
                val tagTokens = cleanQuery.split("[\\s,]+".toRegex()).filter { it.isNotBlank() }
                for (tag in tagTokens) {
                    urlBuilder.addQueryParameter("IncludedTags", tag)
                }
            }
        } else {
            // Query parameter
            if (query.isNotBlank() && source.searchParam.isNotBlank()) {
                urlBuilder.addQueryParameter(source.searchParam, query.trim())
            }

            // Pagination parameter
            if (source.pageParam.isNotBlank()) {
                val actualPage = (page - 1) + source.pageStartsAt
                urlBuilder.addQueryParameter(source.pageParam, actualPage.toString())
            }

            // Page size parameter
            if (source.pageSizeParam.isNotBlank()) {
                urlBuilder.addQueryParameter(source.pageSizeParam, source.defaultPageSize.toString())
            }
        }

        // Effective Authentication Query Parameters (multi-parameter support including api_key, user_id, etc.)
        val authParams = source.getEffectiveAuthQueryParams()
        for ((key, value) in authParams) {
            if (key.isNotBlank() && value.isNotBlank()) {
                urlBuilder.addQueryParameter(key, value)
            }
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("User-Agent", "ArtfluxApp/2.0 (Android; MediaBrowser)")
            .addHeader("Accept", "application/json")

        if (isWaifu) {
            requestBuilder.addHeader("Accept-Version", "v7")
        }

        // Effective Authentication Headers (Bearer token, custom headers, etc.)
        val headers = source.getEffectiveHeaders()
        for ((hName, hVal) in headers) {
            if (hName.isNotBlank() && hVal.isNotBlank()) {
                requestBuilder.addHeader(hName, hVal)
            }
        }

        return requestBuilder.build()
    }
}
