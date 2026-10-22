package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedArtworkSheet(state: RelatedArtworkState, quality: ThumbnailQuality, blur: Boolean,
    onTag: (String) -> Unit, onMore: () -> Unit, onOpen: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    val local = state.localItems
    val localKeys = local.map { it.libraryKey }.toSet()
    val remote = state.remoteItems.filterNot { it.libraryKey in localKeys }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("related_artwork_sheet")) {
            Text("Related artwork", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall)
            Text("Shared tags and artists · ${state.filters.rating.label} · ${state.filters.mediaType.label}", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag -> FilterChip(state.selectedTag == tag, { onTag(tag) }, label = { Text(tag) }) }
            }
            LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(150.dp), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp) {
                if (local.isNotEmpty()) item(span = StaggeredGridItemSpan.FullLine) { Text("Saved and recently loaded", style = MaterialTheme.typography.titleSmall) }
                items(local, key = { "local:${it.libraryKey}" }) { item -> MediaCard(item, quality = quality, blurNsfw = blur, onClick = { onOpen(item) }) }
                if (state.source != null && state.selectedTag != null && state.source.searchParam.isNotBlank()) {
                    item(span = StaggeredGridItemSpan.FullLine) { Text("From ${state.source.name}", style = MaterialTheme.typography.titleSmall) }
                    items(remote, key = { "remote:${it.libraryKey}" }) { item -> MediaCard(item, quality = quality, blurNsfw = blur, onClick = { onOpen(item) }) }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Column {
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
                            else if (!state.reachedEnd) TextButton(onMore, Modifier.fillMaxWidth().testTag("related_load_more")) { Text(if (state.error != null) "Retry" else "Load more") }
                        }
                    }
                } else item(span = StaggeredGridItemSpan.FullLine) {
                    Text(if (state.tags.isEmpty()) "This artwork has no searchable tags. Related saved artwork may still match its artist." else "The original source is unavailable or does not support tag searches. Showing local matches.", style = MaterialTheme.typography.bodySmall)
                }
                if (local.isEmpty() && remote.isEmpty() && !state.loading) item(span = StaggeredGridItemSpan.FullLine) {
                    Text("No related artwork found. Try another tag or save more artwork to build local matches.", style = MaterialTheme.typography.bodyMedium)
                }
                item(span = StaggeredGridItemSpan.FullLine) { TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Back to artwork") } }
            }
        }
    }
}
