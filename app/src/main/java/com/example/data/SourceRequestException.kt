package com.example.data

import com.example.model.SourceCondition
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLException

/** Public messages never include response bodies, credential values, or request URLs. */
class SourceRequestException(
    val condition: SourceCondition,
    val httpStatus: Int = 0,
    val retryAt: Long = 0
) : IOException(condition.guidance) {
    companion object {
        fun http(code: Int, retryAfter: String? = null, now: Long = System.currentTimeMillis()): SourceRequestException {
            val condition = when (code) {
                401 -> SourceCondition.AUTH_REQUIRED
                403 -> SourceCondition.ACCESS_DENIED
                429 -> SourceCondition.RATE_LIMITED
                in 500..599 -> SourceCondition.SERVER_ERROR
                404, 405, 410, 422 -> SourceCondition.CONFIGURATION
                else -> SourceCondition.UNKNOWN
            }
            val retryAt = if (code == 429 || code == 503) retryTime(retryAfter, now, if (code == 429) 60 else 0) else 0
            return SourceRequestException(condition, code, retryAt)
        }

        internal fun retryTime(value: String?, now: Long, fallbackSeconds: Long): Long {
            val seconds = value?.trim()?.toLongOrNull()
            if (seconds != null) return now + seconds.coerceIn(0, 86400) * 1000
            val date = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                }.parse(value.orEmpty())?.time
            }.getOrNull()
            return date?.coerceIn(now, now + 86_400_000) ?: if (fallbackSeconds > 0) now + fallbackSeconds * 1000 else 0
        }

        fun from(error: Throwable): SourceRequestException = when (error) {
            is SourceRequestException -> error
            is UnknownHostException, is SocketTimeoutException, is SSLException, is IOException -> SourceRequestException(SourceCondition.UNREACHABLE)
            is IllegalArgumentException, is org.json.JSONException -> SourceRequestException(SourceCondition.CONFIGURATION)
            else -> SourceRequestException(SourceCondition.UNKNOWN)
        }
    }
}
