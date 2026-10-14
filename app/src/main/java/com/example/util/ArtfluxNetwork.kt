package com.example.util

import android.net.Uri

/**
 * Centralized network and anti-hotlink header resolution for media loading, video streaming,
 * and background downloads across supported Booru CDNs.
 */
object ArtfluxNetwork {
    const val DEFAULT_USER_AGENT = "ArtfluxApp/2.0 (Android; MediaBrowser)"

    /**
     * Resolves the required anti-hotlink Referer header for a given URL across supported CDNs.
     * Prevents HTTP 403 Forbidden responses.
     */
    fun getRefererForUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase().trim()
        return when {
            lower.contains("gelbooru.com") -> "https://gelbooru.com/"
            lower.contains("danbooru") || lower.contains("donmai.us") -> "https://danbooru.donmai.us/"
            lower.contains("safebooru.org") || lower.contains("safebooru") -> "https://safebooru.org/"
            lower.contains("yande.re") -> "https://yande.re/"
            lower.contains("waifu.im") -> "https://waifu.im/"
            else -> {
                try {
                    val uri = Uri.parse(url)
                    val host = uri.host
                    if (!host.isNullOrBlank()) {
                        "${uri.scheme ?: "https"}://$host/"
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Resolves standard request headers including User-Agent and Referer for the given URL.
     */
    fun getHeadersForUrl(url: String?): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to DEFAULT_USER_AGENT)
        getRefererForUrl(url)?.let { headers["Referer"] = it }
        return headers
    }
}
