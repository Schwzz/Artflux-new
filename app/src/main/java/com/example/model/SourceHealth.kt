package com.example.model

enum class SourceCondition(val label: String, val guidance: String) {
    HEALTHY("Connected", "The source responded successfully."),
    AUTH_REQUIRED("Login needed", "Check this source's API key, user ID, or authorization settings."),
    ACCESS_DENIED("Access restricted", "The source refused access. Check credentials or open its website for access requirements."),
    RATE_LIMITED("Rate limited", "The source received too many requests. Wait before trying again."),
    UNREACHABLE("Connection unavailable", "Check your connection, then retry. The source may also be unavailable."),
    SERVER_ERROR("Source unavailable", "The source is having trouble. Try again later."),
    CONFIGURATION("Check configuration", "Check the API address and response field mappings in source settings."),
    UNKNOWN("Request failed", "Retry or run source diagnostics for more detail.")
}

data class SourceHealth(
    val condition: SourceCondition,
    val checkedAt: Long,
    val lastSuccessAt: Long = 0,
    val retryAt: Long = 0,
    val httpStatus: Int = 0
) {
    fun canRetry(now: Long = System.currentTimeMillis()) = now >= retryAt
}
