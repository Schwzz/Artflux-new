package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MediaItem
import com.example.util.ArtfluxImageLoader
import com.example.util.ArtfluxNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun VideoPlayerContent(
    item: MediaItem,
    isCurrentPage: Boolean,
    loopVideo: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(item.id) { mutableStateOf<String?>(null) }
    var videoRetryTrigger by remember(item.id) { mutableIntStateOf(0) }

    // Verify video stream responsiveness and detect HTTP 403 or HTML errors
    LaunchedEffect(item.id, isCurrentPage, videoRetryTrigger) {
        if (isCurrentPage) {
            withContext(Dispatchers.IO) {
                try {
                    val okHttpClient = OkHttpClient.Builder()
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .readTimeout(12, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                    val req = Request.Builder()
                        .url(item.actualMediaUrl)
                        .header("Range", "bytes=0-1024")
                    for ((k, v) in ArtfluxNetwork.getHeadersForUrl(item.actualMediaUrl)) {
                        req.header(k, v)
                    }
                    okHttpClient.newCall(req.build()).execute().use { resp ->
                        val code = resp.code
                        val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                        if (code == 403) {
                            if (playbackError == null) {
                                playbackError = "ERROR_CODE_IO_BAD_HTTP_STATUS: HTTP 403 Forbidden (CDN anti-hotlink denied access)"
                            }
                        } else if (code in 400..599) {
                            if (playbackError == null) {
                                playbackError = "ERROR_CODE_IO_BAD_HTTP_STATUS: HTTP $code (${resp.message.ifBlank { "Error response" }})"
                            }
                        } else if (contentType.isNotBlank() && (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("text/plain"))) {
                            if (playbackError == null) {
                                playbackError = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED: Expected video stream but server returned '$contentType'"
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    val exoPlayer = remember(item.id, isCurrentPage, loopVideo, videoRetryTrigger) {
        if (isCurrentPage) {
            val requestHeaders = ArtfluxNetwork.getHeadersForUrl(item.actualMediaUrl)

            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(ArtfluxNetwork.DEFAULT_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(requestHeaders)

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    val media3ItemBuilder = Media3Item.Builder().setUri(Uri.parse(item.actualMediaUrl))
                    val clean = item.actualMediaUrl.substringBefore('?').substringBefore('#').lowercase()
                    when {
                        clean.endsWith(".mp4") -> media3ItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
                        clean.endsWith(".webm") -> media3ItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_WEBM)
                        clean.endsWith(".mkv") -> media3ItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MATROSKA)
                    }
                    setMediaItem(media3ItemBuilder.build())
                    repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            val codeName = error.errorCodeName
                            val cause = error.cause
                            val causeDetails = when (cause) {
                                is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> {
                                    "HTTP ${cause.responseCode} (${cause.responseMessage ?: "Error"})"
                                }
                                is androidx.media3.datasource.HttpDataSource.InvalidContentTypeException -> {
                                    "Invalid Content-Type: ${cause.contentType} (expected video stream)"
                                }
                                is androidx.media3.datasource.HttpDataSource.HttpDataSourceException -> {
                                    cause.message ?: "Network stream failure"
                                }
                                else -> {
                                    cause?.message ?: error.message ?: "Playback failure"
                                }
                            }
                            playbackError = "$codeName: $causeDetails"
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                playbackError = null
                            }
                        }
                    })
                    prepare()
                }
        } else null
    }

    DisposableEffect(item.id, isCurrentPage, videoRetryTrigger) {
        onDispose {
            exoPlayer?.pause()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer?.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 60.dp, bottom = 80.dp, start = 8.dp, end = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Instant preview placeholder while video prepares/buffers
        val videoPreviewUrl = item.previewUrl
        if (videoPreviewUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(videoPreviewUrl)
                    .crossfade(false)
                    .build(),
                imageLoader = ArtfluxImageLoader.get(context),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Playback error overlay
        if (playbackError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Playback Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Playback Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = playbackError ?: "Unable to stream media",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    FilledTonalButton(
                        onClick = {
                            playbackError = null
                            videoRetryTrigger++
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        },
                        modifier = Modifier.testTag("video_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Playback")
                    }
                }
            }
        }
    }
}
