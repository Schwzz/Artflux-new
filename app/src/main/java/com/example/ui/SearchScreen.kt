package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FilterState
import com.example.model.MediaItem
import com.example.model.MediaSourceConfig
import com.example.model.ThumbnailQuality
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingSkeletonGrid
import com.example.ui.components.MediaCard
import com.example.ui.components.SearchFilterSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchState: FeedState,
    sources: List<MediaSourceConfig>,
    thumbnailQuality: ThumbnailQuality,
    blurNsfw: Boolean = false,
    gridState: LazyStaggeredGridState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFilterChange: (FilterState) -> Unit,
    onSelectSource: (MediaSourceConfig) -> Unit,
    onOpenPicker: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onResetFilters: () -> Unit,
    onOpenLightbox: (Int) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onTagClick: (String) -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search & Filter Header (Clean, Aligned Single Row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Input / Trigger taking weight(1f)
            if (isSearchExpanded) {
                BasicTextField(
                    value = searchState.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester)
                        .testTag("search_text_input"),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        onSearch()
                    }),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search tags, artists...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                            if (searchState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        onQueryChange("")
                                        onSearch()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    isSearchExpanded = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Collapse search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { isSearchExpanded = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        if (searchState.searchQuery.isNotBlank()) {
                            Text(
                                text = searchState.searchQuery,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Search tags, artists, concepts...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (searchState.searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                onSearch()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Consolidated Filter Action Button aligned beside search bar
            Button(
                onClick = { isFilterSheetOpen = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (searchState.filterState.activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (searchState.filterState.activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("search_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Open filters",
                    tint = if (searchState.filterState.activeFilterCount > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (searchState.filterState.activeFilterCount > 0) "Filter (${searchState.filterState.activeFilterCount})" else "Filter",
                    color = if (searchState.filterState.activeFilterCount > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Search Results Feed with Pull-To-Refresh
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PullToRefreshBox(
                isRefreshing = searchState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    searchState.isLoading && !searchState.isRefreshing -> {
                        LoadingSkeletonGrid()
                    }

                    searchState.errorMessage != null -> {
                        ErrorStateView(
                            errorMessage = searchState.errorMessage,
                            onRetry = onRetry
                        )
                    }

                    searchState.mediaItems.isEmpty() -> {
                        EmptyStateView(
                            hasFilters = searchState.filterState.activeFilterCount > 0 || searchState.searchQuery.isNotBlank(),
                            onReset = onResetFilters
                        )
                    }

                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalItemSpacing = 10.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_results_grid")
                        ) {
                            itemsIndexed(
                                items = searchState.mediaItems,
                                key = { index, item -> "${item.id}_search_$index" }
                            ) { index, item ->
                                MediaCard(
                                    item = item,
                                    quality = thumbnailQuality,
                                    blurNsfw = blurNsfw,
                                    onClick = { onOpenLightbox(index) },
                                    onDownloadClick = { onDownload(item) },
                                    onTagClick = onTagClick
                                )
                            }

                            if (searchState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Consolidated Search Filter Sheet
    if (isFilterSheetOpen) {
        SearchFilterSheet(
            sources = sources,
            activeSource = searchState.activeSource,
            filterState = searchState.filterState,
            currentQuery = searchState.searchQuery,
            onApply = { newSource, newFilter, newQuery ->
                if (newSource.id != searchState.activeSource.id) {
                    onSelectSource(newSource)
                }
                onFilterChange(newFilter)
                if (newQuery != searchState.searchQuery) {
                    onQueryChange(newQuery)
                    onSearch()
                }
                isFilterSheetOpen = false
            },
            onDismiss = { isFilterSheetOpen = false }
        )
    }
}
