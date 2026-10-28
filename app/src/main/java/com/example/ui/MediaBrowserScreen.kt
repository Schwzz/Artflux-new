package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.model.*
import com.example.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

enum class MainTab { HOME, LIBRARY, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserScreen(viewModel: MediaBrowserViewModel, modifier: Modifier = Modifier) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val home by viewModel.homeState.collectAsStateWithLifecycle()
    val browsing by viewModel.browsing.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storage by viewModel.offlineStorage.collectAsStateWithLifecycle()
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()
    val checkingSources by viewModel.checkingSources.collectAsStateWithLifecycle()
    val related by viewModel.relatedArtwork.collectAsStateWithLifecycle()
    val palette by viewModel.colorPalette.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val quality by viewModel.thumbnailQuality.collectAsStateWithLifecycle()
    val blur by viewModel.blurNsfw.collectAsStateWithLifecycle()
    val loop by viewModel.loopVideoPlayback.collectAsStateWithLifecycle()
    val saved by viewModel.savedItems.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val index by viewModel.lightboxIndex.collectAsStateWithLifecycle()
    val lightbox by viewModel.lightboxItems.collectAsStateWithLifecycle()
    val addItem by viewModel.addToCollectionItem.collectAsStateWithLifecycle()
    val downloadItem by viewModel.downloadTargetItem.collectAsStateWithLifecycle()
    val addSource by viewModel.isAddSourceOpen.collectAsStateWithLifecycle()
    val editing by viewModel.editingSourceConfig.collectAsStateWithLifecycle()
    val diagnosticSource by viewModel.diagnosticSource.collectAsStateWithLifecycle()
    val report by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val diagnosing by viewModel.isDiagnosing.collectAsStateWithLifecycle()
    val pendingBackup by viewModel.pendingBackup.collectAsStateWithLifecycle()
    val message by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var showSources by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showBlocks by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    val host = remember { SnackbarHostState() }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = permissionAction; permissionAction = null
        if (granted) action?.invoke() else viewModel.notify("Storage permission is needed for gallery downloads on this Android version")
    }
    fun publicDownload(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionAction = action; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else action()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(viewModel::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::readBackup) }
    LaunchedEffect(message) {
        message?.let { host.currentSnackbarData?.dismiss(); host.showSnackbar(it); viewModel.dismissSnackbar() }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (isActive) { viewModel.refreshDownloads(first); first = false; delay(2500) }
        }
    }
    Scaffold(modifier = modifier.fillMaxSize(), snackbarHost = { SnackbarHost(host) }, bottomBar = {
        ArtfluxBottomBar(tab) { tab = it }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.HOME -> key(home.activeSource.id, home.submittedQuery, home.filterState) {
                    HomeRoute(viewModel, home, browsing, quality, blur, { showSources = true }, { showFilters = true }, sourceHealth[home.activeSource.id], { showHealth = true })
                }
                MainTab.LIBRARY -> LibraryHub(saved, collections, downloads, browsing, quality, blur,
                    onOpen = viewModel::openLibraryItems, onCreate = viewModel::createCollection, onRename = viewModel::renameCollection,
                    onDelete = viewModel::deleteCollection, onAdd = viewModel::bulkAddToCollection, onRemove = viewModel::bulkRemove,
                    onDownload = { items, offline -> if (offline) viewModel.queueDownloads(items, true) else publicDownload { viewModel.queueDownloads(items, false) } },
                    onRetry = { record -> if (record.offline) viewModel.retryDownload(record) else publicDownload { viewModel.retryDownload(record) } }, onRemoveDownload = viewModel::removeDownload, onRemoveFromCollection = viewModel::bulkRemoveFromCollection,
                    onStorage = { showStorage = true })
                MainTab.SETTINGS -> SettingsScreen(sources = sources, theme = theme, onThemeChange = viewModel::setTheme,
                    colorPalette = palette, onColorPaletteChange = viewModel::setColorPalette, blurNsfw = blur, onBlurNsfwChange = viewModel::setBlurNsfw,
                    thumbnailQuality = quality, onThumbnailQualityChange = viewModel::setThumbnailQuality,
                    loopVideo = loop, onLoopVideoChange = viewModel::setLoopVideoPlayback,
                    onAddSourceClick = { viewModel.openAddSourceDialog() }, onEditSource = viewModel::openEditSourceDialog,
                    onCopySource = viewModel::duplicateSource, onTestSource = viewModel::openSourceDiagnostics,
                    onDeleteSource = viewModel::deleteSource,
                    onExportSource = { source -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Source configuration", viewModel.exportSourceConfig(source))) },
                    extraSettings = {
                        OutlinedButton({ showHealth = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.MonitorHeart, null); Spacer(Modifier.width(8.dp)); Text("Source health") }
                        OutlinedButton({ showStorage = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Storage, null); Spacer(Modifier.width(8.dp)); Text("Offline storage") }
                        Spacer(Modifier.height(16.dp))
                        PersonalSettings(browsing, viewModel::setGridDensity, { showBlocks = true }, { export.launch("Artflux-backup.json") }, { restore.launch(arrayOf("application/json", "text/plain")) })
                    })
            }
        }
    }
    if (showSources) ModalBottomSheet(onDismissRequest = { showSources = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Your sources", style = MaterialTheme.typography.headlineSmall)
            Text("Pin favorites for quick access.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(sources.sortedByDescending { it.id in browsing.favorites }, key = { it.id }) { source ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ viewModel.setHomeSource(source); showSources = false }, Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(source.name, color = if (source.id == home.activeSource.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(sourceHealth[source.id]?.condition?.label ?: "Not checked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton({ viewModel.favoriteSource(source.id) }) { Icon(if (source.id in browsing.favorites) Icons.Default.Star else Icons.Default.StarBorder, "Favorite ${source.name}") }
                    }
                }
            }
            FilledTonalButton({ showSources = false; viewModel.openAddSourceDialog() }, Modifier.fillMaxWidth()) { Text("Add a source") }
            TextButton({ showSources = false; showHealth = true }, Modifier.fillMaxWidth()) { Text("Source health") }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showFilters) HomeFeedSettingsSheet(sources = sources, currentSource = home.activeSource, currentFilter = home.filterState,
        onApply = { source, filters -> viewModel.setHomeSourceAndFilter(source, filters); showFilters = false }, onDismiss = { showFilters = false })
    if (showBlocks) BlockedTagsSheet(browsing, viewModel::blockTag, viewModel::toggleBlockedTag, viewModel::removeBlockedTag) { showBlocks = false }
    if (showHealth) SourceHealthSheet(sources, sourceHealth, checkingSources, viewModel::checkSourceHealth,
        onDiagnostics = { showHealth = false; viewModel.openSourceDiagnostics(it) },
        onEdit = { showHealth = false; viewModel.openEditSourceDialog(it) }, onDismiss = { showHealth = false })
    if (showStorage) OfflineStorageSheet(downloads, storage, viewModel::scanOfflineStorage, viewModel::removeOfflineDownloads,
        viewModel::retryDownload, onDismiss = { showStorage = false })
    if (addSource) AddSourceDialog(initialConfig = editing, onDismiss = viewModel::closeAddSourceDialog, onSave = {
        if (editing != null) viewModel.updateCustomSource(it) else viewModel.addCustomSource(it)
    })
    diagnosticSource?.let { source -> SourceDiagnosticsDialog(source, report, diagnosing,
        onRunDiagnostics = { viewModel.runDiagnosticsForSource(source) }, onEditSource = viewModel::openEditSourceDialog, onDismiss = viewModel::dismissSourceDiagnostics) }
    downloadItem?.let { item -> DownloadConfigDialog(item, onConfirm = { q, filename -> publicDownload { viewModel.downloadMediaWithQuality(item, q, filename) } }, onDismiss = viewModel::dismissDownloadPrompt) }
    addItem?.let { item -> AddToCollectionDialog(item, collections, onToggleCollection = viewModel::toggleMediaInCollection,
        onCreateCollection = viewModel::createCollection, onDismiss = viewModel::closeAddToCollectionDialog) }
    pendingBackup?.let { backup -> AlertDialog(onDismissRequest = viewModel::dismissBackup, title = { Text("Restore this backup?") },
        text = { Text("Merge ${backup.items.size} saved artworks, ${backup.collections.size} collections, and ${backup.sources.size} source configurations. Appearance and browsing settings will be restored. Existing source credentials are kept.") },
        confirmButton = { TextButton(viewModel::restoreBackup) { Text("Restore") } }, dismissButton = { TextButton(viewModel::dismissBackup) { Text("Cancel") } }) }
    index?.let { currentIndex -> lightbox.getOrNull(currentIndex)?.let { item ->
        key(lightbox) { LightboxViewer(item = item, currentIndex = currentIndex, totalCount = lightbox.size, itemsList = lightbox, loopVideo = loop,
            onIndexChanged = viewModel::setLightboxIndex, onClose = viewModel::closeLightbox,
            onDownload = { viewModel.promptDownload(item) }, onTagClick = { viewModel.searchHome(it); tab = MainTab.HOME }, onBlockTag = viewModel::blockTag,
            isSaved = { it in savedIds }, onToggleSave = viewModel::toggleSave, onOpenAddToCollection = viewModel::openAddToCollectionDialog, onUndoUnsave = viewModel::undoLastUnsave,
            onDownloadItem = viewModel::promptDownload, onExploreRelated = viewModel::openRelatedArtwork, mediaActive = related?.open != true) }
    } }
    related?.takeIf { it.open }?.let { RelatedArtworkSheet(it, quality, blur, viewModel::selectRelatedTag,
        viewModel::loadMoreRelatedArtwork, viewModel::openRelatedResult, viewModel::dismissRelatedArtwork, viewModel::selectRelatedArtist) }
}

@Composable
private fun HomeRoute(vm: MediaBrowserViewModel, state: FeedState, browsing: BrowsingPreferences, quality: ThumbnailQuality, blur: Boolean, sources: () -> Unit, filters: () -> Unit, health: SourceHealth?, healthDetails: () -> Unit) {
    val grid = rememberLazyStaggeredGridState()
    var restoring by remember { mutableStateOf(true) }
    val sourceId = state.activeSource.id
    val query = state.submittedQuery
    val filter = state.filterState
    LaunchedEffect(Unit) {
        val target = browsing.sessions[sourceId] ?: SourceSession()
        vm.homeState.first { !it.isLoading && !it.isRefreshing }
        var attempts = 0
        while (vm.homeState.value.mediaItems.size <= target.scrollIndex && !vm.homeState.value.hasReachedEnd &&
            vm.homeState.value.errorMessage == null && vm.homeState.value.paginationError == null && target.scrollIndex > 0 && attempts++ < 20) {
            vm.loadNextHomePage()
            vm.homeState.first { !it.isLoadingMore && !it.isLoading && !it.isRefreshing }
        }
        if (vm.homeState.value.mediaItems.isNotEmpty()) grid.scrollToItem(target.scrollIndex.coerceAtMost(vm.homeState.value.mediaItems.lastIndex), target.scrollOffset)
        restoring = false
    }
    LaunchedEffect(restoring) {
        if (!restoring) snapshotFlow { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }.collectLatest { (index, offset) ->
            delay(400); vm.rememberSourceScroll(sourceId, query, filter, index, offset)
        }
    }
    DisposableEffect(Unit) { onDispose { if (!restoring) vm.rememberSourceScroll(sourceId, query, filter, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset) } }
    val more by remember { derivedStateOf {
        val info = grid.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
    } }
    LaunchedEffect(more, state.currentPage, state.isLoadingMore, state.isLoading, state.isRefreshing, restoring) {
        if (more && !restoring && state.mediaItems.isNotEmpty()) vm.loadNextHomePage()
    }
    HomeHub(state, browsing, grid, quality, blur, vm::setHomeQuery, vm::searchHome, sources, filters, vm::setHomeFilterState,
        vm::refreshHome, vm::retryHomePage, { vm.openLightbox(it, state.mediaItems) }, vm::promptDownload,
        vm::saveCurrentSearch, vm::openSavedSearch, vm::removeSavedSearch, vm::clearHistory, health, healthDetails)
}
