package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FilterState
import com.example.ui.components.AddSourceDialog
import com.example.ui.components.AddToCollectionDialog
import com.example.ui.components.ArtfluxBottomBar
import com.example.ui.components.CollectionDetailScreen
import com.example.ui.components.DownloadConfigDialog
import com.example.ui.components.HomeFeedSettingsSheet
import com.example.ui.components.LightboxViewer
import com.example.ui.components.SavesAndCollectionsScreen
import com.example.ui.components.SourceDiagnosticsDialog
import com.example.ui.components.SourcePickerBottomSheet

enum class MainTab {
    HOME, SEARCH, SETTINGS
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScreen(
    viewModel: MediaBrowserViewModel,
    modifier: Modifier = Modifier
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()
    val blurNsfw by viewModel.blurNsfw.collectAsStateWithLifecycle()
    val thumbnailQuality by viewModel.thumbnailQuality.collectAsStateWithLifecycle()
    val loopVideo by viewModel.loopVideoPlayback.collectAsStateWithLifecycle()

    val lightboxIndex by viewModel.lightboxIndex.collectAsStateWithLifecycle()
    val lightboxItems by viewModel.lightboxItems.collectAsStateWithLifecycle()
    val isAddSourceOpen by viewModel.isAddSourceOpen.collectAsStateWithLifecycle()
    val editingSourceConfig by viewModel.editingSourceConfig.collectAsStateWithLifecycle()
    val diagnosticSource by viewModel.diagnosticSource.collectAsStateWithLifecycle()
    val diagnosticReport by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val isDiagnosing by viewModel.isDiagnosing.collectAsStateWithLifecycle()
    val isSourcePickerOpen by viewModel.isSourcePickerOpen.collectAsStateWithLifecycle()
    val downloadTargetItem by viewModel.downloadTargetItem.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val savedItems by viewModel.savedItems.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val addToCollectionItem by viewModel.addToCollectionItem.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isHomeSettingsOpen by remember { mutableStateOf(false) }
    var isSavesAndCollectionsOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val homeGridState = rememberLazyStaggeredGridState()
    val searchGridState = rememberLazyStaggeredGridState()

    // Show snackbar alerts
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissSnackbar()
        }
    }

    // Infinite scrolling trigger for Home
    val shouldLoadMoreHome by remember {
        derivedStateOf {
            val total = homeGridState.layoutInfo.totalItemsCount
            val last = homeGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMoreHome) {
        if (shouldLoadMoreHome && currentTab == MainTab.HOME) {
            viewModel.loadNextHomePage()
        }
    }

    // Infinite scrolling trigger for Search
    val shouldLoadMoreSearch by remember {
        derivedStateOf {
            val total = searchGridState.layoutInfo.totalItemsCount
            val last = searchGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMoreSearch) {
        if (shouldLoadMoreSearch && currentTab == MainTab.SEARCH) {
            viewModel.loadNextSearchPage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ArtfluxBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                selectedCollectionId != null -> {
                    val selectedCol = collections.find { it.id == selectedCollectionId }
                    if (selectedCol != null) {
                        CollectionDetailScreen(
                            collection = selectedCol,
                            items = viewModel.getItemsForCollection(selectedCol.id),
                            thumbnailQuality = thumbnailQuality,
                            blurNsfw = blurNsfw,
                            onRemoveFromCollection = { colId, mediaId ->
                                val mediaItem = savedItems.find { it.id == mediaId }
                                if (mediaItem != null) {
                                    viewModel.toggleMediaInCollection(colId, mediaItem)
                                }
                            },
                            onRenameCollection = { colId, newName ->
                                viewModel.renameCollection(colId, newName)
                            },
                            onDeleteCollection = { colId ->
                                viewModel.deleteCollection(colId)
                            },
                            onBack = { viewModel.clearSelectedCollection() },
                            onOpenLightbox = { index, itemsList ->
                                viewModel.openLightbox(index, itemsList)
                            }
                        )
                    } else {
                        viewModel.clearSelectedCollection()
                    }
                }
                isSavesAndCollectionsOpen -> {
                    SavesAndCollectionsScreen(
                        savedItems = savedItems,
                        collections = collections,
                        thumbnailQuality = thumbnailQuality,
                        blurNsfw = blurNsfw,
                        onCreateCollection = { viewModel.createCollection(it) },
                        onSelectCollection = { viewModel.selectCollection(it) },
                        onBack = { isSavesAndCollectionsOpen = false },
                        onOpenLightbox = { index, itemsList ->
                            viewModel.openLightbox(index, itemsList)
                        }
                    )
                }
                else -> {
                    when (currentTab) {
                        MainTab.HOME -> {
                            HomeScreen(
                                homeState = homeState,
                                thumbnailQuality = thumbnailQuality,
                                blurNsfw = blurNsfw,
                                gridState = homeGridState,
                                onOpenFeedSettings = { isHomeSettingsOpen = true },
                                onRefresh = { viewModel.refreshHome() },
                                onRetry = { viewModel.refreshHome() },
                                onResetFilters = {
                                    viewModel.setHomeFilterState(FilterState())
                                    viewModel.refreshHome()
                                },
                                onOpenLightbox = { index ->
                                    viewModel.openLightbox(index, homeState.mediaItems)
                                },
                                onDownload = { viewModel.promptDownload(it) },
                                onTagClick = { tag ->
                                    currentTab = MainTab.SEARCH
                                    viewModel.setSearchQuery(tag)
                                    viewModel.executeSearchQuery()
                                }
                            )
                        }
                        MainTab.SEARCH -> {
                            SearchScreen(
                                searchState = searchState,
                                sources = sources,
                                thumbnailQuality = thumbnailQuality,
                                blurNsfw = blurNsfw,
                                gridState = searchGridState,
                                onQueryChange = { viewModel.setSearchQuery(it) },
                                onSearch = { viewModel.executeSearchQuery() },
                                onFilterChange = { viewModel.setSearchFilterState(it) },
                                onSelectSource = { viewModel.setSearchSource(it) },
                                onOpenPicker = { viewModel.openSourcePicker("search") },
                                onRefresh = { viewModel.refreshSearch() },
                                onRetry = { viewModel.refreshSearch() },
                                onResetFilters = {
                                    viewModel.setSearchQuery("")
                                    viewModel.setSearchFilterState(FilterState())
                                    viewModel.executeSearchQuery()
                                },
                                onOpenLightbox = { index ->
                                    viewModel.openLightbox(index, searchState.mediaItems)
                                },
                                onDownload = { viewModel.promptDownload(it) },
                                onTagClick = { tag ->
                                    viewModel.setSearchQuery(tag)
                                    viewModel.executeSearchQuery()
                                }
                            )
                        }
                        MainTab.SETTINGS -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            SettingsScreen(
                                sources = sources,
                                theme = theme,
                                onThemeChange = { viewModel.setTheme(it) },
                                colorPalette = colorPalette,
                                onColorPaletteChange = { viewModel.setColorPalette(it) },
                                blurNsfw = blurNsfw,
                                onBlurNsfwChange = { viewModel.setBlurNsfw(it) },
                                thumbnailQuality = thumbnailQuality,
                                onThumbnailQualityChange = { viewModel.setThumbnailQuality(it) },
                                loopVideo = loopVideo,
                                onLoopVideoChange = { viewModel.setLoopVideoPlayback(it) },
                                onAddSourceClick = { viewModel.openAddSourceDialog(null) },
                                onEditSource = { viewModel.openAddSourceDialog(it) },
                                onCopySource = { viewModel.duplicateSource(it) },
                                onTestSource = { viewModel.openSourceDiagnostics(it) },
                                onExportSource = { source ->
                                    val json = viewModel.exportSourceConfig(source)
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("${source.name} Config", json)
                                    clipboard?.setPrimaryClip(clip)
                                },
                                onDeleteSource = { viewModel.deleteSource(it) },
                                savedCount = savedItems.size,
                                collectionsCount = collections.size,
                                onOpenSavesAndCollections = { isSavesAndCollectionsOpen = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Download Configuration Prompt Dialog
    downloadTargetItem?.let { targetItem ->
        DownloadConfigDialog(
            item = targetItem,
            onConfirm = { quality, customFilename ->
                viewModel.downloadMediaWithQuality(targetItem, quality, customFilename)
            },
            onDismiss = {
                viewModel.dismissDownloadPrompt()
            }
        )
    }

    // Add to Collection Dialog overlay
    addToCollectionItem?.let { item ->
        AddToCollectionDialog(
            mediaItem = item,
            collections = collections,
            onToggleCollection = { colId, media -> viewModel.toggleMediaInCollection(colId, media) },
            onCreateCollection = { name -> viewModel.createCollection(name) },
            onDismiss = { viewModel.closeAddToCollectionDialog() }
        )
    }

    // Home Feed Settings / Filter Bottom Sheet
    if (isHomeSettingsOpen) {
        HomeFeedSettingsSheet(
            sources = sources,
            currentSource = homeState.activeSource,
            currentFilter = homeState.filterState,
            onApply = { newSource, newFilter ->
                viewModel.setHomeSourceAndFilter(newSource, newFilter)
                isHomeSettingsOpen = false
            },
            onDismiss = { isHomeSettingsOpen = false }
        )
    }

    // Source Picker Bottom Sheet (Used by Search source switcher)
    if (isSourcePickerOpen) {
        val currentActiveSource = if (currentTab == MainTab.SEARCH) searchState.activeSource else homeState.activeSource
        SourcePickerBottomSheet(
            sources = sources,
            activeSource = currentActiveSource,
            onSelectSource = {
                if (currentTab == MainTab.SEARCH) {
                    viewModel.setSearchSource(it)
                } else {
                    viewModel.setHomeSource(it)
                }
            },
            onDeleteSource = { viewModel.deleteSource(it) },
            onAddNewSource = { viewModel.openAddSourceDialog(null) },
            onDismiss = { viewModel.closeSourcePicker() }
        )
    }

    // Add / Edit Source Dialog (Add Source 2.0 Flow)
    if (isAddSourceOpen) {
        AddSourceDialog(
            initialConfig = editingSourceConfig,
            onDismiss = { viewModel.closeAddSourceDialog() },
            onSave = { newSource ->
                if (editingSourceConfig != null) {
                    viewModel.updateCustomSource(newSource)
                } else {
                    viewModel.addCustomSource(newSource)
                }
            }
        )
    }

    // Source Diagnostics Dialog (from Settings / Diagnostics)
    diagnosticSource?.let { sourceToTest ->
        SourceDiagnosticsDialog(
            source = sourceToTest,
            report = diagnosticReport,
            isLoading = isDiagnosing,
            onRunDiagnostics = { viewModel.runDiagnosticsForSource(sourceToTest) },
            onEditSource = { source -> viewModel.openEditSourceDialog(source) },
            onDismiss = { viewModel.dismissSourceDiagnostics() }
        )
    }

    // Fullscreen Swipeable Lightbox Viewer Overlay
    lightboxIndex?.let { index ->
        val currentItem = lightboxItems.getOrNull(index)
        if (currentItem != null) {
            LightboxViewer(
                item = currentItem,
                currentIndex = index,
                totalCount = lightboxItems.size,
                itemsList = lightboxItems,
                loopVideo = loopVideo,
                onIndexChanged = { newIdx -> viewModel.setLightboxIndex(newIdx) },
                onPrevious = { viewModel.previousLightboxItem() },
                onNext = { viewModel.nextLightboxItem() },
                onClose = { viewModel.closeLightbox() },
                onDownload = { viewModel.promptDownload(currentItem) },
                onTagClick = { tag ->
                    currentTab = MainTab.SEARCH
                    viewModel.setSearchQuery(tag)
                    viewModel.executeSearchQuery()
                },
                isSaved = { mediaId -> savedIds.contains(mediaId) },
                onToggleSave = { viewModel.toggleSave(it) },
                onOpenAddToCollection = { viewModel.openAddToCollectionDialog(it) },
                onUndoUnsave = { viewModel.undoLastUnsave() }
            )
        }
    }
}
