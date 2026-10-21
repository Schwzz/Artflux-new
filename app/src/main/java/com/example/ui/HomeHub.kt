package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHub(
    state: FeedState, browsing: BrowsingPreferences, grid: LazyStaggeredGridState,
    thumbnailQuality: ThumbnailQuality, blurNsfw: Boolean,
    onQueryChange: (String) -> Unit, onSearch: (String) -> Unit, onSources: () -> Unit, onFilters: () -> Unit,
    onFilterChange: (FilterState) -> Unit, onRefresh: () -> Unit, onMore: () -> Unit,
    onOpen: (Int) -> Unit, onDownload: (MediaItem) -> Unit, onSaveSearch: () -> Unit,
    onSavedSearch: (SavedSearch) -> Unit, onRemoveSavedSearch: (String) -> Unit, onClearHistory: () -> Unit
) {
    var searchOpen by rememberSaveable { mutableStateOf(state.submittedQuery.isNotBlank()) }
    val focus = LocalFocusManager.current
    fun submit(query: String) { focus.clearFocus(); onSearch(query) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { searchOpen = !searchOpen }, Modifier.testTag("home_search_button")) {
                Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, if (searchOpen) "Close search" else "Search artwork")
            }
            TextButton(onClick = onSources, modifier = Modifier.weight(1f).testTag("home_source_button")) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ARTFLUX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.activeSource.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
            }
            IconButton(onClick = onFilters, modifier = Modifier.testTag("home_filter_button")) {
                BadgedBox(badge = { if (state.filterState.activeFilterCount > 0) Badge { Text("${state.filterState.activeFilterCount}") } }) {
                    Icon(Icons.Default.Tune, "Feed filters")
                }
            }
        }
        if (searchOpen) {
            OutlinedTextField(value = state.searchQuery, onValueChange = onQueryChange, singleLine = true,
                placeholder = { Text("Search tags, artists, or subjects") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit(state.searchQuery) }),
                trailingIcon = { IconButton({ submit(state.searchQuery) }) { Icon(Icons.Default.ArrowForward, "Submit search") } },
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("home_search_field"))
            val suggestions = browsing.suggestions(state.searchQuery)
            if (suggestions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { tag -> SuggestionChip(onClick = {
                        val prefix = state.searchQuery.substringBeforeLast(' ', "")
                        onQueryChange((if (prefix.isNotBlank()) "$prefix " else "") + tag + " ")
                    }, label = { Text(tag) }) }
                }
            } else if (state.searchQuery.isBlank() && browsing.history.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    browsing.history.take(8).forEach { query -> SuggestionChip({ submit(query) }, label = { Text(query) }, icon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) }) }
                    TextButton(onClearHistory) { Text("Clear history") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.submittedQuery.isBlank()) "Discover something new" else "Search results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (state.submittedQuery.isBlank()) "A little inspiration, one artwork at a time." else state.submittedQuery,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onSaveSearch, Modifier.testTag("save_search_button")) { Icon(Icons.Default.BookmarkAdd, "Save this search and filters") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val f = state.filterState
            if (state.submittedQuery.isNotBlank()) InputChip(true, { submit("") }, label = { Text("${state.submittedQuery} ×") })
            if (f.mediaType != MediaType.ALL) InputChip(true, { onFilterChange(f.copy(mediaType = MediaType.ALL)) }, label = { Text("${f.mediaType.label} ×") })
            if (f.rating != MediaRating.ALL) InputChip(true, { onFilterChange(f.copy(rating = MediaRating.ALL)) }, label = { Text("${f.rating.label} ×") })
            if (f.orientation != Orientation.ALL) InputChip(true, { onFilterChange(f.copy(orientation = Orientation.ALL)) }, label = { Text("${f.orientation.name.lowercase()} ×") })
            if (f.sort != SortOption.LATEST) InputChip(true, { onFilterChange(f.copy(sort = SortOption.LATEST)) }, label = { Text("${f.sort.label} ×") })
        }
        if (browsing.savedSearches.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                browsing.savedSearches.forEach { search -> InputChip(selected = false, onClick = { onSavedSearch(search) }, label = { Text(search.name, maxLines = 1) },
                    trailingIcon = { IconButton({ onRemoveSavedSearch(search.id) }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Remove saved search", Modifier.size(16.dp)) } }) }
            }
        }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> LoadingSkeletonGrid()
                state.errorMessage != null && state.mediaItems.isEmpty() -> ErrorStateView(state.errorMessage, onRefresh)
                state.mediaItems.isEmpty() -> EmptyStateView(state.filterState.activeFilterCount > 0 || state.submittedQuery.isNotBlank(),
                    onReset = { onFilterChange(FilterState()); submit("") }, onLoadMore = onMore.takeIf { !state.hasReachedEnd }, paginationError = state.paginationError)
                else -> LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(browsing.density.tileWidth.dp), state = grid,
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize().testTag("home_artwork_grid")) {
                    itemsIndexed(state.mediaItems, key = { _, item -> item.id }) { index, item ->
                        MediaCard(item = item, quality = thumbnailQuality, blurNsfw = blurNsfw, onClick = { onOpen(index) },
                            onDownloadClick = { onDownload(item) }, onTagClick = { tag -> onQueryChange(tag); searchOpen = true; submit(tag) })
                    }
                    if (!state.hasReachedEnd) item { FeedPaginationFooter(state.isLoadingMore, state.paginationError, onMore) }
                }
            }
        }
    }
}
