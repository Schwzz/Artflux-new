package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AuthType(val label: String) {
    NONE("None"),
    QUERY_PARAMS("Query Parameters"),
    BEARER_TOKEN("Bearer Token"),
    CUSTOM_HEADER("Custom Header")
}

data class AuthParam(
    val key: String,
    val value: String
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("key", key)
        obj.put("value", value)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AuthParam {
            return AuthParam(
                key = obj.optString("key", ""),
                value = obj.optString("value", "")
            )
        }
    }
}

data class MediaSourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apiUrl: String,
    val searchParam: String = "q",
    val pageParam: String = "page",
    val pageStartsAt: Int = 1,
    val pageSizeParam: String = "limit",
    val defaultPageSize: Int = 25,
    val apiKey: String = "",
    val apiKeyHeader: String = "",
    val apiKeyInQuery: Boolean = false,
    val apiKeyQueryParam: String = "api_key",
    val authType: AuthType = AuthType.NONE,
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val authQueryParams: List<AuthParam> = emptyList(),
    val itemsPath: String = "",
    val imageUrlField: String = "sample_url",
    val thumbUrlField: String = "preview_url",
    val sampleUrlField: String = "sample_url",
    val postUrlField: String = "id",
    val tagsField: String = "tags",
    val ratingField: String = "rating",
    val mediaTypeField: String = "",
    val titleField: String = "tags",
    val authorField: String = "owner",
    val isBuiltIn: Boolean = false,
    val description: String = "",
    val gifQueryTag: String = "",
    val videoQueryTag: String = "",
    val safeRatingTag: String = "",
    val suggestiveRatingTag: String = "",
    val adultRatingTag: String = ""
) {
    val canDiscoverGifs: Boolean
        get() = gifQueryTag.isNotBlank()

    val canDiscoverVideos: Boolean
        get() = videoQueryTag.isNotBlank()

    val supportsGifs: Boolean
        get() = true

    val supportsVideos: Boolean
        get() = true

    val hasAuthentication: Boolean
        get() = authType != AuthType.NONE ||
                apiKey.isNotBlank() ||
                authHeaderValue.isNotBlank() ||
                authQueryParams.any { it.key.isNotBlank() && it.value.isNotBlank() }

    /**
     * Returns media types that Artflux can render and filter for this source.
     * All sources support IMAGE, GIF, and VIDEO in Artflux.
     */
    fun getSupportedMediaTypes(): List<MediaType> {
        if (apiUrl.contains("waifu.im") || id.contains("waifu")) {
            return listOf(MediaType.ALL)
        }
        return listOf(MediaType.ALL, MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO)
    }

    /**
     * Returns media types that this source can specifically discover/query via server-side tags.
     */
    fun getDiscoverableMediaTypes(): List<MediaType> {
        val list = mutableListOf(MediaType.ALL, MediaType.IMAGE)
        if (canDiscoverGifs) list.add(MediaType.GIF)
        if (canDiscoverVideos) list.add(MediaType.VIDEO)
        return list
    }

    /**
     * Resolves effective query parameters for authentication.
     * Merges modern authQueryParams and legacy apiKeyInQuery.
     */
    fun getEffectiveAuthQueryParams(sanitizeSecrets: Boolean = false): Map<String, String> {
        val map = mutableMapOf<String, String>()

        // 1. Modern auth query params
        for (param in authQueryParams) {
            if (param.key.isNotBlank()) {
                val value = if (sanitizeSecrets && param.value.isNotBlank()) {
                    when (param.key.lowercase()) {
                        "user_id", "user", "uid" -> "YOUR_USER_ID"
                        else -> "YOUR_API_KEY"
                    }
                } else {
                    param.value
                }
                map[param.key.trim()] = value.trim()
            }
        }

        // 2. Legacy apiKey in query parameter fallback
        if (map.isEmpty() && apiKey.isNotBlank() && (apiKeyInQuery || authType == AuthType.QUERY_PARAMS)) {
            val keyParam = apiKeyQueryParam.ifBlank { "api_key" }
            val value = if (sanitizeSecrets) "YOUR_API_KEY" else apiKey
            map[keyParam] = value
        }

        return map
    }

    /**
     * Resolves effective HTTP headers for authentication.
     */
    fun getEffectiveHeaders(sanitizeSecrets: Boolean = false): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        when (authType) {
            AuthType.BEARER_TOKEN -> {
                val token = if (sanitizeSecrets) "YOUR_BEARER_TOKEN" else authHeaderValue.ifBlank { apiKey }
                if (token.isNotBlank()) {
                    headers["Authorization"] = "Bearer $token"
                }
            }
            AuthType.CUSTOM_HEADER -> {
                val headerName = authHeaderName.ifBlank { apiKeyHeader.ifBlank { "Authorization" } }
                val headerVal = if (sanitizeSecrets) "YOUR_API_KEY" else authHeaderValue.ifBlank { apiKey }
                if (headerName.isNotBlank() && headerVal.isNotBlank()) {
                    headers[headerName] = headerVal
                }
            }
            else -> {
                // Legacy header support
                if (apiKey.isNotBlank() && !apiKeyInQuery && apiKeyHeader.isNotBlank()) {
                    val headerVal = if (sanitizeSecrets) "YOUR_API_KEY" else apiKey
                    headers[apiKeyHeader] = headerVal
                }
            }
        }

        return headers
    }

    fun toJson(sanitizeSecrets: Boolean = false): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("apiUrl", apiUrl)
        json.put("searchParam", searchParam)
        json.put("pageParam", pageParam)
        json.put("pageStartsAt", pageStartsAt)
        json.put("pageSizeParam", pageSizeParam)
        json.put("defaultPageSize", defaultPageSize)
        json.put("itemsPath", itemsPath)
        json.put("imageUrlField", imageUrlField)
        json.put("thumbUrlField", thumbUrlField)
        json.put("sampleUrlField", sampleUrlField)
        json.put("postUrlField", postUrlField)
        json.put("tagsField", tagsField)
        json.put("ratingField", ratingField)
        json.put("mediaTypeField", mediaTypeField)
        json.put("titleField", titleField)
        json.put("authorField", authorField)
        json.put("isBuiltIn", isBuiltIn)
        json.put("description", description)
        json.put("gifQueryTag", gifQueryTag)
        json.put("videoQueryTag", videoQueryTag)
        json.put("safeRatingTag", safeRatingTag)
        json.put("suggestiveRatingTag", suggestiveRatingTag)
        json.put("adultRatingTag", adultRatingTag)

        // Auth properties
        json.put("authType", authType.name)
        json.put("authHeaderName", authHeaderName)
        json.put("authHeaderValue", if (sanitizeSecrets && authHeaderValue.isNotBlank()) "YOUR_API_KEY" else authHeaderValue)

        val paramsArray = JSONArray()
        for (param in authQueryParams) {
            val pObj = JSONObject()
            pObj.put("key", param.key)
            pObj.put("value", if (sanitizeSecrets && param.value.isNotBlank()) {
                if (param.key.contains("user", true)) "YOUR_USER_ID" else "YOUR_API_KEY"
            } else param.value)
            paramsArray.put(pObj)
        }
        json.put("authQueryParams", paramsArray)

        // Legacy auth fields
        json.put("apiKey", if (sanitizeSecrets && apiKey.isNotBlank()) "YOUR_API_KEY" else apiKey)
        json.put("apiKeyHeader", apiKeyHeader)
        json.put("apiKeyInQuery", apiKeyInQuery)
        json.put("apiKeyQueryParam", apiKeyQueryParam)

        // Structured authentication block for export/AI format compatibility
        val authObj = JSONObject()
        val authTypeName = when (authType) {
            AuthType.NONE -> if (apiKey.isNotBlank()) (if (apiKeyInQuery) "query" else "header") else "none"
            AuthType.QUERY_PARAMS -> "query"
            AuthType.BEARER_TOKEN -> "bearer"
            AuthType.CUSTOM_HEADER -> "header"
        }
        authObj.put("type", authTypeName)
        if (authType == AuthType.CUSTOM_HEADER || (authType == AuthType.NONE && apiKeyHeader.isNotBlank())) {
            authObj.put("headerName", authHeaderName.ifBlank { apiKeyHeader })
            authObj.put("headerValue", if (sanitizeSecrets) "YOUR_API_KEY" else authHeaderValue.ifBlank { apiKey })
        } else if (authType == AuthType.BEARER_TOKEN) {
            authObj.put("headerName", "Authorization")
            authObj.put("headerValue", if (sanitizeSecrets) "YOUR_BEARER_TOKEN" else authHeaderValue.ifBlank { apiKey })
        }
        val qParamsObj = JSONObject()
        val effectiveQParams = getEffectiveAuthQueryParams(sanitizeSecrets)
        for ((k, v) in effectiveQParams) {
            qParamsObj.put(k, v)
        }
        if (qParamsObj.length() > 0) {
            authObj.put("parameters", qParamsObj)
        }
        json.put("authentication", authObj)

        return json
    }

    /**
     * Exports clean, structured Artflux JSON configuration without internal runtime fields.
     */
    fun toExportJson(sanitizeSecrets: Boolean = true): String {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("apiUrl", apiUrl)
        obj.put("searchParam", searchParam)
        obj.put("pageParam", pageParam)
        obj.put("pageStartsAt", pageStartsAt)
        obj.put("pageSizeParam", pageSizeParam)
        obj.put("defaultPageSize", defaultPageSize)
        obj.put("itemsPath", itemsPath)
        obj.put("imageUrlField", imageUrlField)
        obj.put("thumbUrlField", thumbUrlField)
        obj.put("sampleUrlField", sampleUrlField)
        obj.put("postUrlField", postUrlField)
        obj.put("tagsField", tagsField)
        obj.put("ratingField", ratingField)
        obj.put("mediaTypeField", mediaTypeField)
        obj.put("gifQueryTag", gifQueryTag)
        obj.put("videoQueryTag", videoQueryTag)
        obj.put("safeRatingTag", safeRatingTag)
        obj.put("suggestiveRatingTag", suggestiveRatingTag)
        obj.put("adultRatingTag", adultRatingTag)
        obj.put("description", description)

        // Authentication structure
        val authObj = JSONObject()
        val authTypeName = when (authType) {
            AuthType.NONE -> if (apiKey.isNotBlank()) (if (apiKeyInQuery) "query" else "header") else "none"
            AuthType.QUERY_PARAMS -> "query"
            AuthType.BEARER_TOKEN -> "bearer"
            AuthType.CUSTOM_HEADER -> "header"
        }
        authObj.put("type", authTypeName)
        if (authType == AuthType.CUSTOM_HEADER || apiKeyHeader.isNotBlank()) {
            authObj.put("headerName", authHeaderName.ifBlank { apiKeyHeader })
            authObj.put("headerValue", if (sanitizeSecrets) "YOUR_API_KEY" else authHeaderValue.ifBlank { apiKey })
        } else if (authType == AuthType.BEARER_TOKEN) {
            authObj.put("headerName", "Authorization")
            authObj.put("headerValue", if (sanitizeSecrets) "Bearer YOUR_TOKEN" else authHeaderValue.ifBlank { apiKey })
        }
        val qParams = getEffectiveAuthQueryParams(sanitizeSecrets)
        if (qParams.isNotEmpty()) {
            val pObj = JSONObject()
            for ((k, v) in qParams) {
                pObj.put(k, v)
            }
            authObj.put("parameters", pObj)
        }
        obj.put("authentication", authObj)

        return obj.toString(2)
    }

    companion object {
        fun fromJson(json: JSONObject): MediaSourceConfig {
            // Parse authentication if available
            val authObj = json.optJSONObject("authentication")
            var resolvedAuthType = try {
                AuthType.valueOf(json.optString("authType", AuthType.NONE.name))
            } catch (e: Exception) {
                AuthType.NONE
            }
            var resolvedHeaderName = json.optString("authHeaderName", "")
            var resolvedHeaderVal = json.optString("authHeaderValue", "")
            val resolvedQueryParams = mutableListOf<AuthParam>()

            // 1. Read from authQueryParams array if present
            val rawParamsArray = json.optJSONArray("authQueryParams")
            if (rawParamsArray != null) {
                for (i in 0 until rawParamsArray.length()) {
                    val pObj = rawParamsArray.optJSONObject(i) ?: continue
                    val k = pObj.optString("key", "").trim()
                    val v = pObj.optString("value", "").trim()
                    if (k.isNotBlank()) {
                        resolvedQueryParams.add(AuthParam(k, v))
                    }
                }
            }

            // 2. Read structured "authentication" block if present
            if (authObj != null) {
                val typeStr = authObj.optString("type", "").lowercase().trim()
                when (typeStr) {
                    "query", "query_params" -> {
                        resolvedAuthType = AuthType.QUERY_PARAMS
                    }
                    "bearer", "bearer_token" -> {
                        resolvedAuthType = AuthType.BEARER_TOKEN
                        resolvedHeaderName = "Authorization"
                        resolvedHeaderVal = authObj.optString("headerValue", authObj.optString("token", ""))
                    }
                    "header", "custom_header" -> {
                        resolvedAuthType = AuthType.CUSTOM_HEADER
                        resolvedHeaderName = authObj.optString("headerName", "Authorization")
                        resolvedHeaderVal = authObj.optString("headerValue", authObj.optString("apiKey", ""))
                    }
                    "none" -> {
                        resolvedAuthType = AuthType.NONE
                    }
                }

                val paramsObj = authObj.optJSONObject("parameters")
                if (paramsObj != null) {
                    val keys = paramsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = paramsObj.optString(key, "")
                        if (key.isNotBlank() && resolvedQueryParams.none { it.key == key }) {
                            resolvedQueryParams.add(AuthParam(key, value))
                        }
                    }
                }
            }

            // 3. Legacy flat fields fallback
            val legacyApiKey = json.optString("apiKey", "")
            val legacyApiKeyHeader = json.optString("apiKeyHeader", "")
            val legacyApiKeyInQuery = json.optBoolean("apiKeyInQuery", false)
            val legacyApiKeyQueryParam = json.optString("apiKeyQueryParam", "api_key")

            if (resolvedAuthType == AuthType.NONE && legacyApiKey.isNotBlank()) {
                if (legacyApiKeyInQuery) {
                    resolvedAuthType = AuthType.QUERY_PARAMS
                    if (resolvedQueryParams.none { it.key == legacyApiKeyQueryParam }) {
                        resolvedQueryParams.add(AuthParam(legacyApiKeyQueryParam, legacyApiKey))
                    }
                } else if (legacyApiKeyHeader.isNotBlank()) {
                    resolvedAuthType = AuthType.CUSTOM_HEADER
                    resolvedHeaderName = legacyApiKeyHeader
                    resolvedHeaderVal = legacyApiKey
                }
            }

            return MediaSourceConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", json.optString("title", "Custom Source")).trim(),
                apiUrl = json.optString("apiUrl", json.optString("api_url", json.optString("url", ""))).trim(),
                searchParam = json.optString("searchParam", json.optString("search_param", "q")).trim(),
                pageParam = json.optString("pageParam", json.optString("page_param", "page")).trim(),
                pageStartsAt = json.optInt("pageStartsAt", json.optInt("page_starts_at", 1)),
                pageSizeParam = json.optString("pageSizeParam", json.optString("page_size_param", "limit")).trim(),
                defaultPageSize = json.optInt("defaultPageSize", json.optInt("default_page_size", 25)),
                apiKey = legacyApiKey,
                apiKeyHeader = legacyApiKeyHeader,
                apiKeyInQuery = legacyApiKeyInQuery,
                apiKeyQueryParam = legacyApiKeyQueryParam,
                authType = resolvedAuthType,
                authHeaderName = resolvedHeaderName,
                authHeaderValue = resolvedHeaderVal,
                authQueryParams = resolvedQueryParams,
                itemsPath = json.optString("itemsPath", json.optString("items_path", json.optString("results_path", ""))).trim(),
                imageUrlField = json.optString("imageUrlField", json.optString("image_url_field", json.optString("file_url_field", "sample_url"))).trim(),
                thumbUrlField = json.optString("thumbUrlField", json.optString("thumb_url_field", json.optString("preview_url_field", "preview_url"))).trim(),
                sampleUrlField = json.optString("sampleUrlField", json.optString("sample_url_field", "sample_url")).trim(),
                postUrlField = json.optString("postUrlField", json.optString("post_url_field", "id")).trim(),
                tagsField = json.optString("tagsField", json.optString("tags_field", "tags")).trim(),
                ratingField = json.optString("ratingField", json.optString("rating_field", "rating")).trim(),
                mediaTypeField = json.optString("mediaTypeField", json.optString("media_type_field", "")).trim(),
                titleField = json.optString("titleField", json.optString("title_field", "tags")).trim(),
                authorField = json.optString("authorField", json.optString("author_field", "owner")).trim(),
                isBuiltIn = json.optBoolean("isBuiltIn", false),
                description = json.optString("description", "").trim(),
                gifQueryTag = json.optString("gifQueryTag", json.optString("gif_query_tag", "")).trim(),
                videoQueryTag = json.optString("videoQueryTag", json.optString("video_query_tag", "")).trim(),
                safeRatingTag = json.optString("safeRatingTag", json.optString("safe_rating_tag", "")).trim(),
                suggestiveRatingTag = json.optString("suggestiveRatingTag", json.optString("suggestive_rating_tag", "")).trim(),
                adultRatingTag = json.optString("adultRatingTag", json.optString("adult_rating_tag", "")).trim()
            )
        }

        fun parseJson(rawJson: String): Result<MediaSourceConfig> {
            return try {
                val trimmed = rawJson.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                    return Result.failure(IllegalArgumentException("Configuration must be a valid JSON object enclosed in { }."))
                }
                val json = JSONObject(trimmed)
                val config = fromJson(json)
                val validationErrors = validateConfig(config)
                if (validationErrors.isNotEmpty()) {
                    return Result.failure(IllegalArgumentException(validationErrors.joinToString("\n")))
                }
                Result.success(config)
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Invalid JSON syntax: ${e.message ?: "Failed to parse"}"))
            }
        }

        fun validateConfig(config: MediaSourceConfig): List<String> {
            val errors = mutableListOf<String>()
            if (config.name.isBlank()) {
                errors.add("Source Name is required.")
            }
            if (config.apiUrl.isBlank()) {
                errors.add("API Endpoint URL is required.")
            } else if (!config.apiUrl.startsWith("http://") && !config.apiUrl.startsWith("https://")) {
                errors.add("API Endpoint URL must start with http:// or https://")
            }
            if (config.imageUrlField.isBlank()) {
                errors.add("Image URL Field mapping is required.")
            }
            return errors
        }

        const val AI_PROMPT_TEMPLATE = """You are an expert API integration assistant for the Artflux Android media browser.
Your task is to analyze the API documentation, endpoint details, or sample JSON response provided below and generate a complete, valid Artflux Media Source JSON configuration.

OUTPUT FORMAT REQUIREMENTS:
Output ONLY a single JSON block with the following schema:

{
  "name": "<Source Name>",
  "apiUrl": "<Full API endpoint URL including query parameters if applicable>",
  "searchParam": "<Query parameter for search tags/keywords, e.g. tags, q, search, query>",
  "pageParam": "<Query parameter for pagination, e.g. page, pid, p, offset>",
  "pageStartsAt": 1,
  "pageSizeParam": "<Query parameter for page size limit, e.g. limit, per_page, count>",
  "defaultPageSize": 25,
  "itemsPath": "<JSON path to the posts/images array if nested, e.g. 'posts', 'data', 'results', or empty string '' if the root response is already an array>",
  "imageUrlField": "<JSON field path for the full-resolution image/file URL, e.g. 'file_url', 'url', 'image'>",
  "thumbUrlField": "<JSON field path for the thumbnail/preview image URL, e.g. 'preview_url', 'thumb', 'preview_file_url'>",
  "sampleUrlField": "<JSON field path for medium/sample image URL, e.g. 'sample_url', 'large_file_url'>",
  "postUrlField": "<JSON field path for web post/page URL or ID, e.g. 'id', 'link', 'post_url'>",
  "tagsField": "<JSON field path containing tags string or array, e.g. 'tags', 'tag_string'>",
  "ratingField": "<JSON field path containing content rating, e.g. 'rating', 'purity'>",
  "mediaTypeField": "<JSON field path for file extension or media type, e.g. 'file_ext', 'type'>",
  "gifQueryTag": "<Search tag/keyword used by this API to find GIFs, e.g. 'animated', 'gif', or ''>",
  "videoQueryTag": "<Search tag/keyword used by this API to find videos, e.g. 'webm', 'video', 'mp4', or ''>",
  "safeRatingTag": "<Server-side tag for safe content, e.g. 'rating:g', 'rating:safe', or ''>",
  "suggestiveRatingTag": "<Server-side tag for suggestive/questionable content, e.g. 'rating:s,q', 'rating:questionable', or ''>",
  "adultRatingTag": "<Server-side tag for adult/explicit content, e.g. 'rating:e', 'rating:explicit', or ''>",
  "authentication": {
    "type": "none",
    "parameters": {
      "api_key": "YOUR_API_KEY",
      "user_id": "YOUR_USER_ID"
    },
    "headerName": "",
    "headerValue": ""
  },
  "description": "<Short 1-line description of the source>"
}

RULES:
1. Do NOT invent fields. If a field cannot be determined, set it to "" (or 1 for pageStartsAt, 25 for defaultPageSize).
2. For authentication parameters and keys, ALWAYS use placeholder values like "YOUR_API_KEY" or "YOUR_USER_ID". Never output real secrets.
3. If the API uses multiple query parameters for authentication (like Gelbooru with api_key and user_id), specify them in authentication.parameters.
4. If something is uncertain, briefly explain it after the JSON block.

---
[PASTE API DOCUMENTATION OR SAMPLE JSON RESPONSE BELOW THIS LINE]"""

        // --- BUILT-IN SOURCES ---

        val BUILT_IN_SAFEBOORU = MediaSourceConfig(
            id = "builtin_safebooru",
            name = "Safebooru",
            apiUrl = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1",
            searchParam = "tags",
            pageParam = "pid",
            pageStartsAt = 0,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "sample_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            titleField = "tags",
            authorField = "owner",
            isBuiltIn = true,
            description = "Safe anime illustration archive with tag-based search",
            gifQueryTag = "animated",
            videoQueryTag = "",
            safeRatingTag = "rating:general",
            suggestiveRatingTag = "",
            adultRatingTag = ""
        )

        val BUILT_IN_DANBOORU = MediaSourceConfig(
            id = "builtin_danbooru",
            name = "Danbooru",
            apiUrl = "https://danbooru.donmai.us/posts.json",
            searchParam = "tags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "file_url",
            thumbUrlField = "preview_file_url",
            sampleUrlField = "large_file_url",
            postUrlField = "id",
            tagsField = "tag_string",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            titleField = "tag_string",
            authorField = "tag_string_artist",
            isBuiltIn = true,
            description = "Premier anime image board with extensive tagging and ratings",
            gifQueryTag = "animated_gif",
            videoQueryTag = "webm",
            safeRatingTag = "rating:g",
            suggestiveRatingTag = "rating:s,q",
            adultRatingTag = "rating:e"
        )

        val BUILT_IN_YANDERE = MediaSourceConfig(
            id = "builtin_yandere",
            name = "Yande.re",
            apiUrl = "https://yande.re/post.json",
            searchParam = "tags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "file_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            titleField = "tags",
            authorField = "author",
            isBuiltIn = true,
            description = "High-resolution anime illustrations and scans",
            gifQueryTag = "",
            videoQueryTag = "",
            safeRatingTag = "rating:s",
            suggestiveRatingTag = "rating:q",
            adultRatingTag = "rating:e"
        )

        val BUILT_IN_WAIFU_IM = MediaSourceConfig(
            id = "builtin_waifu_im",
            name = "Waifu.im",
            apiUrl = "https://api.waifu.im/images",
            searchParam = "IncludedTags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "pageSize",
            defaultPageSize = 25,
            itemsPath = "items",
            imageUrlField = "url",
            thumbUrlField = "url",
            sampleUrlField = "url",
            postUrlField = "source",
            tagsField = "tags",
            ratingField = "isNsfw",
            mediaTypeField = "extension",
            titleField = "signature",
            authorField = "artists",
            isBuiltIn = true,
            description = "Anime illustration archive with v7 API tag searching and SFW/NSFW filtering",
            gifQueryTag = "",
            videoQueryTag = "",
            safeRatingTag = "is_nsfw:false",
            suggestiveRatingTag = "",
            adultRatingTag = "is_nsfw:true"
        )

        val DEFAULT_SOURCES = listOf(
            BUILT_IN_SAFEBOORU,
            BUILT_IN_DANBOORU,
            BUILT_IN_YANDERE,
            BUILT_IN_WAIFU_IM
        )

        // --- EXPANDED SOURCE TEMPLATES ---

        val TEMPLATE_WAIFU_IM = MediaSourceConfig(
            name = "Waifu.im API",
            apiUrl = "https://api.waifu.im/images",
            searchParam = "IncludedTags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "pageSize",
            defaultPageSize = 25,
            itemsPath = "items",
            imageUrlField = "url",
            thumbUrlField = "url",
            sampleUrlField = "url",
            postUrlField = "source",
            tagsField = "tags",
            ratingField = "isNsfw",
            mediaTypeField = "extension",
            gifQueryTag = "",
            videoQueryTag = "",
            safeRatingTag = "is_nsfw:false",
            suggestiveRatingTag = "",
            adultRatingTag = "is_nsfw:true",
            description = "Curated anime illustration API with v7 endpoint supporting SFW/NSFW filtering and tag search."
        )

        val TEMPLATE_SAFEBOORU = MediaSourceConfig(
            name = "Safebooru XML/JSON API",
            apiUrl = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1",
            searchParam = "tags",
            pageParam = "pid",
            pageStartsAt = 0,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "sample_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            gifQueryTag = "animated",
            videoQueryTag = "",
            safeRatingTag = "rating:general",
            suggestiveRatingTag = "",
            adultRatingTag = "",
            description = "Public anime illustration archive with tag-based search."
        )

        val TEMPLATE_DANBOORU = MediaSourceConfig(
            name = "Danbooru REST API",
            apiUrl = "https://danbooru.donmai.us/posts.json",
            searchParam = "tags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "file_url",
            thumbUrlField = "preview_file_url",
            sampleUrlField = "large_file_url",
            postUrlField = "id",
            tagsField = "tag_string",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            gifQueryTag = "animated_gif",
            videoQueryTag = "webm",
            safeRatingTag = "rating:g",
            suggestiveRatingTag = "rating:s,q",
            adultRatingTag = "rating:e",
            description = "Standard Danbooru JSON endpoint with full metadata and ratings."
        )

        val TEMPLATE_YANDERE = MediaSourceConfig(
            name = "Yande.re Scans API",
            apiUrl = "https://yande.re/post.json",
            searchParam = "tags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "file_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            gifQueryTag = "",
            videoQueryTag = "",
            safeRatingTag = "rating:s",
            suggestiveRatingTag = "rating:q",
            adultRatingTag = "rating:e",
            description = "High-quality anime illustration scans and artwork repository."
        )

        val TEMPLATE_GELBOORU = MediaSourceConfig(
            name = "Gelbooru API (v0.2)",
            apiUrl = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1",
            searchParam = "tags",
            pageParam = "pid",
            pageStartsAt = 0,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "post",
            imageUrlField = "file_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            gifQueryTag = "animated",
            videoQueryTag = "video",
            safeRatingTag = "rating:general",
            suggestiveRatingTag = "rating:sensitive,questionable",
            adultRatingTag = "rating:explicit",
            authType = AuthType.QUERY_PARAMS,
            authQueryParams = listOf(
                AuthParam("api_key", ""),
                AuthParam("user_id", "")
            ),
            description = "Gelbooru imageboard with optional api_key & user_id query parameters."
        )

        val TEMPLATE_MOEBOORU = MediaSourceConfig(
            name = "Moebooru (Konachan)",
            apiUrl = "https://konachan.net/post.json",
            searchParam = "tags",
            pageParam = "page",
            pageStartsAt = 1,
            pageSizeParam = "limit",
            defaultPageSize = 25,
            itemsPath = "",
            imageUrlField = "file_url",
            thumbUrlField = "preview_url",
            sampleUrlField = "sample_url",
            postUrlField = "id",
            tagsField = "tags",
            ratingField = "rating",
            mediaTypeField = "file_ext",
            gifQueryTag = "animated",
            videoQueryTag = "",
            safeRatingTag = "rating:s",
            suggestiveRatingTag = "rating:q",
            adultRatingTag = "rating:e",
            description = "Moebooru engine endpoint for anime wallpapers and illustrations."
        )

        val SOURCE_TEMPLATES = listOf(
            TEMPLATE_SAFEBOORU,
            TEMPLATE_DANBOORU,
            TEMPLATE_YANDERE,
            TEMPLATE_GELBOORU,
            TEMPLATE_MOEBOORU,
            TEMPLATE_WAIFU_IM
        )
    }
}
