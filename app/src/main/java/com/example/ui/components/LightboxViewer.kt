package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
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
    isSaved: (String) -> Boolean = { false },
    onToggleSave: ((MediaItem) -> Unit)? = null,
    onOpenAddToCollection: ((MediaItem) -> Unit)? = null,
    onUndoUnsave: (() -> Unit)? = null
) {
    val effectiveItems = if (itemsList.isNotEmpty()) itemsList else listOf(item)
    val safeInitialPage = currentIndex.coerceIn(0, (effectiveItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage) { effectiveItems.size }

    var showInfoSheet by remember { mutableStateOf(false) }
    var activeZoomScale by remember { mutableFloatStateOf(1f) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
        activeZoomScale = 1f
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
                key(pageItem.id) {
                    val isCurrentPage = pagerState.currentPage == pageIndex
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
                    }
                )
            }
        }

        // Top Bar: Clean, with Close button and Media Type Indicator (NO "1/75" counter)
        LightboxTopBar(
            item = currentItem,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom Action Bar: Info, Share, Download (Comfortable spacing & system insets)
        LightboxBottomActionBar(
            item = currentItem,
            isSaved = isSaved(currentItem.id),
            showInfoSheet = showInfoSheet,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            onToggleSave = onToggleSave,
            onOpenAddToCollection = onOpenAddToCollection,
            onUndoUnsave = onUndoUnsave,
            onToggleInfoSheet = { showInfoSheet = !showInfoSheet },
            onDownload = onDownload,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarPadding + 84.dp)
                .testTag("lightbox_snackbar_host")
        )

        // Media Details Panel / Sheet with Visible Dismissal Button
        if (showInfoSheet) {
            MediaDetailsSheet(
                item = currentItem,
                onDismiss = { showInfoSheet = false },
                onTagClick = { tag ->
                    showInfoSheet = false
                    onClose()
                    onTagClick(tag)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
