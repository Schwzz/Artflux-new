package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.model.MediaItem
import com.example.util.ArtfluxImageLoader
import com.example.util.ArtfluxNetwork

@Composable
fun ImageViewerContent(
    item: MediaItem, isCurrentPage: Boolean, onZoomScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier, chromeVisible: Boolean = true, resetZoomKey: Int = 0, onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    var scale by remember(item.libraryKey, item.actualMediaUrl) { mutableFloatStateOf(1f) }
    var x by remember(item.libraryKey, item.actualMediaUrl) { mutableFloatStateOf(0f) }
    var y by remember(item.libraryKey, item.actualMediaUrl) { mutableFloatStateOf(0f) }
    var transforming by remember { mutableStateOf(false) }
    var retry by remember(item.libraryKey, item.actualMediaUrl) { mutableIntStateOf(0) }
    var preview by remember(item.libraryKey, item.actualMediaUrl) { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var aspect by remember(item.libraryKey) { mutableFloatStateOf(if (item.width != null && item.height != null && item.height > 0) item.width.toFloat() / item.height else 1f) }
    fun reset() { scale = 1f; x = 0f; y = 0f }
    fun move(nextScale: Float, nextX: Float, nextY: Float) {
        scale = nextScale.coerceIn(1f, 5f)
        val offset = ViewerGeometry.clamp(nextX, nextY, scale, viewport.width.toFloat(), viewport.height.toFloat(), aspect)
        x = offset.x; y = offset.y
    }
    LaunchedEffect(isCurrentPage, resetZoomKey) { reset() }
    LaunchedEffect(scale, isCurrentPage) { if (isCurrentPage) onZoomScaleChanged(scale) }
    LaunchedEffect(viewport, aspect) { move(scale, x, y) }
    val animatedScale by animateFloatAsState(scale, if (transforming) snap() else spring(stiffness = 650f), label = "image zoom")
    val animatedX by animateFloatAsState(x, if (transforming) snap() else spring(stiffness = 650f), label = "image pan x")
    val animatedY by animateFloatAsState(y, if (transforming) snap() else spring(stiffness = 650f), label = "image pan y")
    val local = item.actualMediaUrl.startsWith("content:") || item.actualMediaUrl.startsWith("file:")
    val smallerUrl = item.sampleUrl?.takeIf { it != item.actualMediaUrl && it.startsWith("http") }
        ?: item.previewUrl.takeIf { it.isNotBlank() && it != item.actualMediaUrl && it.startsWith("http") }
    val displayUrl = if (preview) smallerUrl ?: item.actualMediaUrl else item.actualMediaUrl
    val request = remember(displayUrl, retry) {
        ImageRequest.Builder(context).data(displayUrl).crossfade(true).apply {
            ArtfluxNetwork.getHeadersForUrl(displayUrl).forEach { (k, v) -> setHeader(k, v) }
        }.build()
    }
    Box(modifier.fillMaxSize().padding(top = if (chromeVisible) 70.dp else 0.dp, bottom = if (chromeVisible) 150.dp else 0.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
            .testTag("viewer_image_surface")
            .pointerInput(item.libraryKey) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = { tap ->
                    if (scale > 1.05f) reset()
                    else move(2.5f, (viewport.width / 2f - tap.x) * 1.5f, (viewport.height / 2f - tap.y) * 1.5f)
                })
            }
            .pointerInput(item.libraryKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dragX = 0f
                    var dragY = 0f
                    var panning = false
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2) {
                            transforming = true
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val pivotX = centroid.x - viewport.width / 2f
                            val pivotY = centroid.y - viewport.height / 2f
                            val nextScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val ratio = nextScale / scale
                            move(nextScale, (x - pivotX) * ratio + pivotX + pan.x, (y - pivotY) * ratio + pivotY + pan.y)
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.05f && pointers > 0) {
                            val pan = event.calculatePan()
                            dragX += pan.x; dragY += pan.y
                            if (kotlin.math.hypot(dragX, dragY) > viewConfiguration.touchSlop) panning = true
                            if (panning) {
                                transforming = true
                                move(scale, x + pan.x, y + pan.y)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    transforming = false
                    if (scale <= 1.05f) reset()
                }
            }, contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(model = request, imageLoader = ArtfluxImageLoader.get(context), contentDescription = item.title,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onSuccess = { result ->
                    val drawable = result.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) aspect = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) } },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = animatedScale; scaleY = animatedScale; translationX = animatedX; translationY = animatedY
                }) },
                error = { state ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(Modifier.padding(20.dp)) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Warning, "Load error", tint = MaterialTheme.colorScheme.error)
                            Text("Unable to display artwork", style = MaterialTheme.typography.titleMedium)
                            Text(MediaLoadMessage.forError(state.result.throwable, local), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton({ reset(); retry++ }, Modifier.testTag("media_retry_button")) { Icon(Icons.Default.Refresh, null); Text("Retry") }
                            if (!local && !preview && smallerUrl != null) TextButton({ reset(); preview = true }) { Text("Load smaller preview") }
                        }
                    }
                    }
                })
            if (preview) Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = MaterialTheme.shapes.small) {
                TextButton({ preview = false; reset(); retry++ }) { Text("Preview quality · Load original") }
            }
        }
    }
}
