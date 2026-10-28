package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.MediaItem
import com.example.model.MediaType

@Composable
fun LightboxViewer(
    item: MediaItem,
    currentIndex: Int,
    totalCount: Int,
    itemsList: List<MediaItem> = listOf(item),
    loopVideo: Boolean = true,
    onIndexChanged: (Int) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onTagClick: (String) -> Unit,
    onBlockTag: (String) -> Unit = {},
    isSaved: (String) -> Boolean = { false },
    onToggleSave: ((MediaItem) -> Unit)? = null,
    onOpenAddToCollection: ((MediaItem) -> Unit)? = null,
    onUndoUnsave: (() -> Unit)? = null,
    onDownloadItem: ((MediaItem) -> Unit)? = null,
    onExploreRelated: ((MediaItem, String?) -> Unit)? = null,
    mediaActive: Boolean = true
) {
    val effectiveItems = if (itemsList.isNotEmpty()) itemsList else listOf(item)
    val safeInitialPage = currentIndex.coerceIn(0, (effectiveItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage) { effectiveItems.size }

    var showInfoSheet by remember { mutableStateOf(false) }
    var activeZoomScale by remember { mutableFloatStateOf(1f) }
    var chromeVisible by remember { mutableStateOf(true) }
    var resetZoom by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
        activeZoomScale = 1f
        chromeVisible = true
        showInfoSheet = false
    }

    val currentItem = effectiveItems.getOrNull(pagerState.currentPage) ?: item

    BackHandler {
        if (showInfoSheet) {
            showInfoSheet = false
        } else {
            onClose()
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .testTag("lightbox_viewer")
    ) {
        // Horizontal Pager for fluid swipe navigation
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = activeZoomScale <= 1.05f,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageItem = effectiveItems[pageIndex]

            if (pageItem.mediaType == MediaType.VIDEO) {
                // Video Player with ExoPlayer
                key(pageItem.libraryKey) {
                    val isCurrentPage = pagerState.currentPage == pageIndex && mediaActive
                    VideoPlayerContent(
                        item = pageItem,
                        isCurrentPage = isCurrentPage,
                        loopVideo = loopVideo
                    )
                }
            } else {
                // Image / GIF Viewer with Pinch-to-Zoom & Pan
                val isCurrentPage = pagerState.currentPage == pageIndex
                ImageViewerContent(
                    item = pageItem,
                    isCurrentPage = isCurrentPage,
                    onZoomScaleChanged = { scale ->
                        activeZoomScale = scale
                    },
                    chromeVisible = chromeVisible,
                    resetZoomKey = resetZoom,
                    onTap = { chromeVisible = !chromeVisible }
                )
            }
        }

        if (chromeVisible) LightboxTopBar(
            item = currentItem,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (chromeVisible) Column(Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }, enabled = pagerState.currentPage > 0,
                    modifier = Modifier.testTag("viewer_previous")) { Icon(Icons.Default.ChevronLeft, "Previous artwork") }
                Text("${pagerState.currentPage + 1} / ${effectiveItems.size}", style = MaterialTheme.typography.labelMedium)
                if (onExploreRelated != null) TextButton({ onExploreRelated(currentItem, null) }, Modifier.testTag("viewer_related")) {
                    Icon(Icons.Default.ImageSearch, null); Text("Similar")
                }
                if (activeZoomScale > 1.05f) IconButton({ resetZoom++ }, Modifier.testTag("viewer_reset_zoom")) { Icon(Icons.Default.ZoomOutMap, "Reset zoom") }
                IconButton({ coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }, enabled = pagerState.currentPage < effectiveItems.lastIndex,
                    modifier = Modifier.testTag("viewer_next")) { Icon(Icons.Default.ChevronRight, "Next artwork") }
            }
            LightboxBottomActionBar(
            item = currentItem,
            isSaved = isSaved(currentItem.libraryKey),
            showInfoSheet = showInfoSheet,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            onToggleSave = onToggleSave,
            onOpenAddToCollection = onOpenAddToCollection,
            onUndoUnsave = onUndoUnsave,
            onToggleInfoSheet = { showInfoSheet = !showInfoSheet },
            onDownload = { if (onDownloadItem != null) onDownloadItem(currentItem) else onDownload() }
            )
        }
        if (!chromeVisible) IconButton({ chromeVisible = true }, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 12.dp).testTag("viewer_show_controls")) {
            Icon(Icons.Default.FullscreenExit, "Show viewer controls")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarPadding + 140.dp)
                .testTag("lightbox_snackbar_host")
        )

        // Media Details Panel / Sheet with Visible Dismissal Button
        if (showInfoSheet) {
            MediaDetailsSheet(
                item = currentItem,
                onDismiss = { showInfoSheet = false },
                onBlockTag = onBlockTag,
                onTagClick = { tag ->
                    showInfoSheet = false
                    if (onExploreRelated != null) onExploreRelated(currentItem, tag)
                    else { onClose(); onTagClick(tag) }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
