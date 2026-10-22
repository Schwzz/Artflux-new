package com.example

import com.example.data.MediaApiClient
import com.example.data.awaitBody
import com.example.model.MediaSourceConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkReliabilityTest {
    @Test fun coroutineCancellationCancelsHttpCallAndToleratesLateFailure() = runTest {
        val call = HeldCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitBody() }
        job.cancel()
        job.join()
        assertTrue(call.isCanceled())
        call.callback.onFailure(call, IOException("Canceled"))
        assertTrue(job.isCancelled)
    }

    @Test fun responseSnapshotReadsBody() = runTest {
        val call = HeldCall()
        var body: String? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { body = call.awaitBody().bodyString }
        call.callback.onResponse(call, Response.Builder().request(call.request())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("[]".toResponseBody()).build())
        job.join()
        assertEquals("[]", body)
    }

    @Test fun httpErrorsRedactSourceSecrets() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Denied secret-token").body("Denied".toResponseBody()).build()
        }.build()
        try {
            val result = MediaApiClient(client).fetchMedia(source().copy(apiKey = "secret-token"))
            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull()!!.message!!.contains("secret-token"))
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun source() = MediaSourceConfig(id = "test", name = "Test", apiUrl = "https://example.org/feed")

    private class HeldCall : Call {
        lateinit var callback: Callback
        private var cancelled = false
        private var executed = false
        override fun request() = Request.Builder().url("https://example.org").build()
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = executed
        override fun timeout() = Timeout()
        override fun clone(): Call = HeldCall()
        override fun execute(): Response = throw UnsupportedOperationException()
    }
}
