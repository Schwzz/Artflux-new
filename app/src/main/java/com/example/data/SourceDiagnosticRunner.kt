package com.example.data

import com.example.model.DiagnosticStatus
import com.example.model.DiagnosticStep
import com.example.model.MediaSourceConfig
import com.example.model.SourceDiagnosticReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SourceDiagnosticRunner(private val client: OkHttpClient) {

    suspend fun diagnoseSource(source: MediaSourceConfig): SourceDiagnosticReport = withContext(Dispatchers.IO) {
        val steps = mutableListOf<DiagnosticStep>()

        val urlBuilder = source.apiUrl.toHttpUrlOrNull()?.newBuilder()
        if (urlBuilder == null) {
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                summary = "Invalid API URL: '${source.apiUrl}'",
                steps = listOf(
                    DiagnosticStep(
                        title = "Connection",
                        status = DiagnosticStatus.FAILED,
                        detail = "The provided API URL could not be parsed into a valid HTTP URL."
                    )
                )
            )
        }

        // Add page param
        if (source.pageParam.isNotBlank()) {
            urlBuilder.addQueryParameter(source.pageParam, source.pageStartsAt.toString())
        }
        if (source.pageSizeParam.isNotBlank()) {
            urlBuilder.addQueryParameter(source.pageSizeParam, source.defaultPageSize.toString())
        }

        // Add Auth query parameters
        val authParams = source.getEffectiveAuthQueryParams()
        for ((k, v) in authParams) {
            if (k.isNotBlank() && v.isNotBlank()) {
                urlBuilder.addQueryParameter(k, v)
            }
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("User-Agent", "ArtfluxApp/2.0 (Android; Diagnostic)")
            .addHeader("Accept", "application/json")

        val headers = source.getEffectiveHeaders()
        for ((hName, hVal) in headers) {
            if (hName.isNotBlank() && hVal.isNotBlank()) {
                requestBuilder.addHeader(hName, hVal)
            }
        }

        val (httpCode, isHttpOk, httpMessage, bodyString) = try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                ResponseSnapshot(
                    code = response.code,
                    isSuccessful = response.isSuccessful,
                    message = response.message,
                    bodyString = response.body?.string().orEmpty()
                )
            }
        } catch (e: Exception) {
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                summary = "Connection failed: ${e.message ?: "Network unreachable"}",
                steps = listOf(
                    DiagnosticStep(
                        title = "Connection",
                        status = DiagnosticStatus.FAILED,
                        detail = "Could not establish connection to ${source.apiUrl}: ${e.message}"
                    ),
                    DiagnosticStep(
                        title = "Authentication",
                        status = DiagnosticStatus.FAILED,
                        detail = "Cannot test authentication because connection failed."
                    )
                )
            )
        }

        // 1. Connection Step
        if (isHttpOk) {
            steps.add(DiagnosticStep("Connection", DiagnosticStatus.PASSED, "HTTP $httpCode OK — Successfully reached endpoint."))
        } else {
            steps.add(DiagnosticStep("Connection", DiagnosticStatus.FAILED, "HTTP $httpCode $httpMessage"))
        }

        // 2. Authentication Step
        val authErrorInBody = detectAuthError(bodyString)
        if (httpCode == 401 || httpCode == 403 || authErrorInBody != null) {
            val rawReason = when {
                authErrorInBody != null -> authErrorInBody
                httpCode == 401 || httpCode == 403 -> "HTTP $httpCode Unauthorized/Forbidden. Check API key, user ID, or header credentials."
                else -> "Authentication failed."
            }
            val sanitizedReason = sanitizeDiagnosticMessage(rawReason, source)
            val actionableDetail = if (authErrorInBody != null) {
                "Authentication failed: $sanitizedReason. Please check that your API credentials (e.g. api_key, user_id) are valid and properly configured."
            } else {
                sanitizedReason
            }

            steps.add(
                DiagnosticStep(
                    "Authentication",
                    DiagnosticStatus.FAILED,
                    actionableDetail
                )
            )
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                httpStatus = httpCode,
                summary = "Authentication failed: $sanitizedReason",
                steps = steps
            )
        } else {
            if (source.hasAuthentication) {
                steps.add(DiagnosticStep("Authentication", DiagnosticStatus.PASSED, "Credentials accepted without authorization error."))
            } else {
                steps.add(DiagnosticStep("Authentication", DiagnosticStatus.PASSED, "Public endpoint (No authentication required)."))
            }
        }

        if (!isHttpOk) {
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                httpStatus = httpCode,
                summary = "Request failed with HTTP $httpCode.",
                steps = steps
            )
        }

        if (bodyString.isBlank()) {
            steps.add(DiagnosticStep("Results", DiagnosticStatus.WARNING, "Server returned a 200 OK response with an empty body."))
            return@withContext SourceDiagnosticReport(
                isSuccess = true,
                httpStatus = httpCode,
                totalItems = 0,
                summary = "Connected successfully, but body was empty.",
                steps = steps
            )
        }

        // 3. Results Path & Items Parsing
        val trimmed = bodyString.trim()
        val itemsArray: JSONArray? = try {
            if (trimmed.startsWith("[")) {
                steps.add(DiagnosticStep("Results", DiagnosticStatus.PASSED, "Root JSON array detected."))
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)
                val targetPath = source.itemsPath.trim()
                if (targetPath.isNotBlank()) {
                    val extracted = JsonPathExtractor.extractNestedJsonArray(rootObj, targetPath)
                    if (extracted != null) {
                        steps.add(DiagnosticStep("Results", DiagnosticStatus.PASSED, "Items array found at path '$targetPath' (${extracted.length()} item(s))."))
                        extracted
                    } else {
                        // Check common alternatives
                        val commonKey = listOf("post", "posts", "data", "results", "images").firstOrNull { rootObj.has(it) }
                        if (commonKey != null) {
                            steps.add(DiagnosticStep("Results", DiagnosticStatus.WARNING, "Results path '$targetPath' was not found, but array exists at '$commonKey'."))
                            rootObj.optJSONArray(commonKey)
                        } else {
                            steps.add(DiagnosticStep("Results", DiagnosticStatus.FAILED, "Results path '$targetPath' was not found in response object."))
                            null
                        }
                    }
                } else {
                    // Try auto-detection
                    val commonKey = listOf("post", "posts", "data", "results", "images").firstOrNull { rootObj.has(it) }
                    if (commonKey != null) {
                        val arr = rootObj.optJSONArray(commonKey)
                        steps.add(DiagnosticStep("Results", DiagnosticStatus.PASSED, "Auto-detected results array under '$commonKey' (${arr?.length() ?: 0} item(s))."))
                        arr
                    } else {
                        steps.add(DiagnosticStep("Results", DiagnosticStatus.WARNING, "No items array found at root or standard paths."))
                        JSONArray()
                    }
                }
            } else {
                steps.add(DiagnosticStep("Results", DiagnosticStatus.FAILED, "Response is not JSON (received HTML or plaintext)."))
                null
            }
        } catch (e: Exception) {
            steps.add(DiagnosticStep("Results", DiagnosticStatus.FAILED, "JSON Parse Error: ${e.message}"))
            null
        }

        if (itemsArray == null) {
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                httpStatus = httpCode,
                summary = "Failed to parse items array from API response.",
                steps = steps
            )
        }

        if (itemsArray.length() == 0) {
            steps.add(DiagnosticStep("Images", DiagnosticStatus.WARNING, "No items available to inspect image URLs."))
            steps.add(DiagnosticStep("Thumbnails", DiagnosticStatus.WARNING, "No items available to inspect thumbnail URLs."))
            steps.add(DiagnosticStep("Tags", DiagnosticStatus.WARNING, "No items available to inspect tags."))
            steps.add(DiagnosticStep("Ratings", DiagnosticStatus.WARNING, "No items available to inspect ratings."))
            steps.add(DiagnosticStep("Pagination", DiagnosticStatus.PASSED, "Page parameter configured: '${source.pageParam}' (starts at ${source.pageStartsAt})."))

            return@withContext SourceDiagnosticReport(
                isSuccess = true,
                httpStatus = httpCode,
                totalItems = 0,
                summary = "Connected to API successfully, but 0 items were returned.",
                steps = steps
            )
        }

        // Inspect first item
        val firstObj = itemsArray.optJSONObject(0)
        if (firstObj == null) {
            steps.add(DiagnosticStep("Images", DiagnosticStatus.FAILED, "First item in array is not a valid JSON object."))
            return@withContext SourceDiagnosticReport(
                isSuccess = false,
                httpStatus = httpCode,
                totalItems = itemsArray.length(),
                summary = "Array elements are not JSON objects.",
                steps = steps
            )
        }

        val parsedItems = MediaResponseParser.parseMediaItems(source, bodyString)
        val firstParsed = parsedItems.firstOrNull()

        // 4. Image URL Mapping
        if (firstParsed != null && firstParsed.imageUrl.isNotBlank()) {
            steps.add(DiagnosticStep("Images", DiagnosticStatus.PASSED, "Image URL mapped successfully: '${firstParsed.imageUrl.take(45)}...'"))
        } else {
            val rawImage = JsonPathExtractor.extractValue(firstObj, source.imageUrlField)
            if (rawImage.isNotBlank()) {
                steps.add(DiagnosticStep("Images", DiagnosticStatus.PASSED, "Image field '${source.imageUrlField}' found: '$rawImage'."))
            } else {
                steps.add(DiagnosticStep("Images", DiagnosticStatus.FAILED, "No image URL found under configured field '${source.imageUrlField}'."))
            }
        }

        // 5. Thumbnail / Preview URL Mapping
        if (firstParsed != null && firstParsed.thumbnailUrl.isNotBlank() && firstParsed.thumbnailUrl != firstParsed.imageUrl) {
            steps.add(DiagnosticStep("Thumbnails", DiagnosticStatus.PASSED, "Preview URL mapped: '${firstParsed.thumbnailUrl.take(45)}...'"))
        } else {
            val rawThumb = JsonPathExtractor.extractValue(firstObj, source.thumbUrlField)
            if (rawThumb.isNotBlank()) {
                steps.add(DiagnosticStep("Thumbnails", DiagnosticStatus.PASSED, "Thumbnail field '${source.thumbUrlField}' found."))
            } else {
                steps.add(DiagnosticStep("Thumbnails", DiagnosticStatus.WARNING, "Thumbnail field '${source.thumbUrlField}' empty (falling back to image URL)."))
            }
        }

        // 6. Tags Extraction
        if (firstParsed != null && firstParsed.tags.isNotEmpty()) {
            steps.add(DiagnosticStep("Tags", DiagnosticStatus.PASSED, "Extracted ${firstParsed.tags.size} tag(s) (e.g. ${firstParsed.tags.take(3).joinToString(", ")})."))
        } else {
            val rawTags = JsonPathExtractor.extractValue(firstObj, source.tagsField)
            if (rawTags.isNotBlank()) {
                steps.add(DiagnosticStep("Tags", DiagnosticStatus.PASSED, "Tags field '${source.tagsField}' found."))
            } else {
                steps.add(DiagnosticStep("Tags", DiagnosticStatus.WARNING, "Tags field '${source.tagsField}' was empty or not found."))
            }
        }

        // 7. Ratings Extraction
        if (firstParsed != null) {
            steps.add(DiagnosticStep("Ratings", DiagnosticStatus.PASSED, "Rating mapped to '${firstParsed.rating.label}' (field: '${source.ratingField}')."))
        } else {
            steps.add(DiagnosticStep("Ratings", DiagnosticStatus.WARNING, "Rating field '${source.ratingField}' not found (defaulting to Unknown)."))
        }

        // 8. Pagination Check
        steps.add(DiagnosticStep("Pagination", DiagnosticStatus.PASSED, "Page param '${source.pageParam}' (start=${source.pageStartsAt}, limit=${source.defaultPageSize})."))

        val allPassed = steps.none { it.status == DiagnosticStatus.FAILED }

        return@withContext SourceDiagnosticReport(
            isSuccess = allPassed,
            httpStatus = httpCode,
            totalItems = parsedItems.size,
            summary = if (allPassed) "All diagnostics passed! Source is ready to use (${parsedItems.size} items loaded)." else "Diagnostic issues detected.",
            steps = steps,
            sampleTitle = firstParsed?.title,
            sampleImageUrl = firstParsed?.imageUrl,
            sampleThumbUrl = firstParsed?.thumbnailUrl
        )
    }

    private data class ResponseSnapshot(
        val code: Int,
        val isSuccessful: Boolean,
        val message: String,
        val bodyString: String
    )

    companion object {
        fun detectAuthError(body: String): String? {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return null

            // Known authentication failure indicators (case-insensitive)
            val authKeywords = listOf(
                "missing authentication",
                "invalid authentication",
                "authentication failed",
                "authentication required",
                "requires authentication",
                "invalid api_key",
                "invalid api key",
                "api_key required",
                "api key required",
                "invalid user_id",
                "invalid user id",
                "user_id required",
                "user id required",
                "unauthorized",
                "access denied",
                "login required",
                "invalid credentials"
            )

            // 1. If it's a JSON object: {"error": "...", "message": "..."}
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    val json = JSONObject(trimmed)
                    val errorKeys = listOf("error", "message", "msg", "reason", "detail", "description")
                    for (key in errorKeys) {
                        val value = json.optString(key, "").trim()
                        if (value.isNotEmpty()) {
                            val valueLower = value.lowercase()
                            if (authKeywords.any { valueLower.contains(it) } ||
                                valueLower.contains("auth") ||
                                valueLower.contains("unauthorized") ||
                                valueLower.contains("forbidden")
                            ) {
                                return value
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Not a valid JSONObject, fall through
                }
            }

            // 2. Check for JSON-quoted string: e.g. "Missing authentication. Go to api.rule34.xxx for more information"
            val unquoted = if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
                trimmed.substring(1, trimmed.length - 1).trim()
            } else {
                trimmed
            }

            // 3. If unquoted plain text matches any keyword
            val unquotedLower = unquoted.lowercase()
            if (authKeywords.any { unquotedLower.contains(it) }) {
                if (unquoted.length < 500 && !unquoted.startsWith("<html", ignoreCase = true) && !unquoted.startsWith("<!doctype", ignoreCase = true)) {
                    return unquoted
                }
                val matchedLine = unquoted.lines().firstOrNull { line ->
                    authKeywords.any { line.lowercase().contains(it) }
                }?.trim()
                if (!matchedLine.isNullOrBlank()) {
                    return matchedLine.take(200)
                }
                return "Authentication required by server"
            }

            return null
        }

        fun sanitizeDiagnosticMessage(message: String, source: MediaSourceConfig): String {
            var sanitized = message
            val sensitiveValues = mutableListOf<String>()
            if (source.apiKey.isNotBlank() && source.apiKey.length > 2) {
                sensitiveValues.add(source.apiKey)
            }
            if (source.authHeaderValue.isNotBlank() && source.authHeaderValue.length > 2) {
                sensitiveValues.add(source.authHeaderValue)
            }
            for (param in source.authQueryParams) {
                if (param.value.isNotBlank() && param.value.length > 2) {
                    sensitiveValues.add(param.value)
                }
            }

            for (secret in sensitiveValues.distinct()) {
                sanitized = sanitized.replace(secret, "[REDACTED]")
            }
            return sanitized
        }
    }
}
