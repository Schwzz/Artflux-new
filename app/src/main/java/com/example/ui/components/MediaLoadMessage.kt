package com.example.ui.components

import coil.network.HttpException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
object MediaLoadMessage {
    fun forError(error: Throwable, local: Boolean): String {
        if (local) return "This downloaded file is unavailable. Check Offline storage in Library, then download another copy."
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val http = causes.filterIsInstance<HttpException>().firstOrNull()?.response?.code
            ?: causes.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        return when (http) {
            401, 403 -> "The source refused access to this file. Check its access requirements or credentials."
            404, 410 -> "This file is no longer available from the source."
            429 -> "The source is limiting requests. Wait a little before retrying."
            in 500..599 -> "The media server is having trouble. Try again later."
            else -> when {
                causes.any { it is SocketTimeoutException } -> "Loading took too long. Check your connection and retry."
                causes.any { it is FileNotFoundException } -> "This media file could not be found."
                causes.any { it is IOException } -> "Unable to load this file. Check your connection and retry."
                else -> "This file could not be displayed. Try again or open the original source from Details."
            }
        }
    }
}
