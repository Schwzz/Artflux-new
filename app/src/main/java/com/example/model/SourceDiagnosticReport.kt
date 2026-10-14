package com.example.model

enum class DiagnosticStatus {
    PASSED,
    WARNING,
    FAILED
}

data class DiagnosticStep(
    val title: String,
    val status: DiagnosticStatus,
    val detail: String
)

data class SourceDiagnosticReport(
    val isSuccess: Boolean,
    val httpStatus: Int = 0,
    val totalItems: Int = 0,
    val summary: String,
    val steps: List<DiagnosticStep> = emptyList(),
    val sampleTitle: String? = null,
    val sampleImageUrl: String? = null,
    val sampleThumbUrl: String? = null
)
