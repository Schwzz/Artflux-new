package com.example.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpResponseSnapshot(
    val code: Int,
    val isSuccessful: Boolean,
    val message: String,
    val bodyString: String
)

/** Cancellation covers both waiting for headers and reading the response body. */
internal suspend fun Call.awaitBody(): HttpResponseSnapshot = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val snapshot = response.use {
                    HttpResponseSnapshot(it.code, it.isSuccessful, it.message, it.body?.string().orEmpty())
                }
                continuation.resume(snapshot)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    })
}
