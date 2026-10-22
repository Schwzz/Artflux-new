package com.example.data

import com.example.model.MediaItem
import com.example.model.MediaSourceConfig
import com.example.model.SourceDiagnosticReport

interface MediaFetcher {
    suspend fun fetchMedia(source: MediaSourceConfig, query: String = "", page: Int = 1): Result<List<MediaItem>>
    suspend fun diagnoseSource(source: MediaSourceConfig): SourceDiagnosticReport
}
