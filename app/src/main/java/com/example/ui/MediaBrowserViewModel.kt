package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaApiClient
import com.example.data.MediaFetcher
import kotlinx.coroutines.CancellationException
import com.example.data.PreferencesRepository
import com.example.data.SourceRepository
import com.example.model.AppTheme
import com.example.model.ColorPalette
import com.example.model.DownloadQuality
import com.example.model.FilterState
import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import com.example.model.Orientation
import com.example.model.SortOption
import com.example.model.ThumbnailQuality
import com.example.util.ArtfluxNetwork
import com.example.util.QueryBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedState(
    val activeSource: MediaSourceConfig = MediaSourceConfig.BUILT_IN_SAFEBOORU,
    val searchQuery: String = "",
    val submittedQuery: String = "",
    val filterState: FilterState = FilterState(),
    val rawItems: List<MediaItem> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val paginationError: String? = null,
    val currentPage: Int = 1,
    val hasReachedEnd: Boolean = false
)

class MediaBrowserViewModel internal constructor(
    application: Application,
    private val apiClient: MediaFetcher
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, MediaApiClient())
    private val repository = SourceRepository(application.applicationContext)
    private val preferencesRepository = PreferencesRepository(application.applicationContext)
    private val savesRepository = com.example.data.SavesRepository(application.applicationContext)

    val sources: StateFlow<List<MediaSourceConfig>> = repository.sources

    // --- Saves & Collections ---
    val savedItems: StateFlow<List<MediaItem>> = savesRepository.savedItems
    val savedIds: StateFlow<Set<String>> = savesRepository.savedIds
    val collections: StateFlow<List<com.example.model.ArtCollection>> = savesRepository.collections

    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId.asStateFlow()

    private val _addToCollectionItem = MutableStateFlow<MediaItem?>(null)
    val addToCollectionItem: StateFlow<MediaItem?> = _addToCollectionItem.asStateFlow()

    // --- Preferences (Persistent) ---
    val theme: StateFlow<AppTheme> = preferencesRepository.theme
    val colorPalette: StateFlow<ColorPalette> = preferencesRepository.colorPalette
    val blurNsfw: StateFlow<Boolean> = preferencesRepository.blurNsfw
    val thumbnailQuality: StateFlow<ThumbnailQuality> = preferencesRepository.thumbnailQuality
    val loopVideoPlayback: StateFlow<Boolean> = preferencesRepository.loopVideo

    // --- Isolated Home Feed State ---
    private val _homeState = MutableStateFlow(
        FeedState(activeSource = MediaSourceConfig.BUILT_IN_SAFEBOORU)
    )
    val homeState: StateFlow<FeedState> = _homeState.asStateFlow()

    // --- Isolated Search State ---
    private val _searchState = MutableStateFlow(
        FeedState(activeSource = MediaSourceConfig.BUILT_IN_SAFEBOORU)
    )
    val searchState: StateFlow<FeedState> = _searchState.asStateFlow()

    // --- Lightbox / Fullscreen Viewer State ---
    private val _lightboxItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val lightboxItems: StateFlow<List<MediaItem>> = _lightboxItems.asStateFlow()

    private val _lightboxIndex = MutableStateFlow<Int?>(null)
    val lightboxIndex: StateFlow<Int?> = _lightboxIndex.asStateFlow()

    // Dialogs & UI state
    private val _isAddSourceOpen = MutableStateFlow(false)
    val isAddSourceOpen: StateFlow<Boolean> = _isAddSourceOpen.asStateFlow()

    private val _editingSourceConfig = MutableStateFlow<MediaSourceConfig?>(null)
    val editingSourceConfig: StateFlow<MediaSourceConfig?> = _editingSourceConfig.asStateFlow()

    private val _diagnosticSource = MutableStateFlow<MediaSourceConfig?>(null)
    val diagnosticSource: StateFlow<MediaSourceConfig?> = _diagnosticSource.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<com.example.model.SourceDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<com.example.model.SourceDiagnosticReport?> = _diagnosticReport.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    private val _isSourcePickerOpen = MutableStateFlow(false)
    val isSourcePickerOpen: StateFlow<Boolean> = _isSourcePickerOpen.asStateFlow()
    private var sourcePickerTarget: String = "home" // "home" or "search"

    private val _downloadTargetItem = MutableStateFlow<MediaItem?>(null)
    val downloadTargetItem: StateFlow<MediaItem?> = _downloadTargetItem.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Backward compatibility delegates for existing tests
    private val _activeSource = MutableStateFlow<MediaSourceConfig>(MediaSourceConfig.BUILT_IN_SAFEBOORU)
    val activeSource: StateFlow<MediaSourceConfig> = _activeSource.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var diagnosticJob: Job? = null

    init {
        // Load initial Home discovery feed
        loadHomeData(isRefresh = true)
        // Load initial Search feed
        loadSearchData(isRefresh = true)
    }

    private fun syncLegacyHomeState() {
        val current = _homeState.value
        _activeSource.value = current.activeSource
        _searchQuery.value = current.searchQuery
        _filterState.value = current.filterState
        _mediaItems.value = current.mediaItems
        _isLoading.value = current.isLoading
        _isLoadingMore.value = current.isLoadingMore
        _errorMessage.value = current.errorMessage
    }

    // ==========================================
    // PREFERENCES MANAGEMENT
    // ==========================================

    fun setTheme(newTheme: AppTheme) {
        preferencesRepository.setTheme(newTheme)
    }

    fun setColorPalette(palette: ColorPalette) {
        preferencesRepository.setColorPalette(palette)
    }

    fun setBlurNsfw(enabled: Boolean) {
        preferencesRepository.setBlurNsfw(enabled)
    }

    fun setThumbnailQuality(quality: ThumbnailQuality) {
        preferencesRepository.setThumbnailQuality(quality)
    }

    fun setLoopVideoPlayback(enabled: Boolean) {
        preferencesRepository.setLoopVideo(enabled)
    }

    // ==========================================
    // SAVES & COLLECTIONS METHODS
    // ==========================================

    fun isSaved(mediaId: String): Boolean = savesRepository.isSaved(mediaId)

    fun toggleSave(item: MediaItem) {
        val wasSaved = isSaved(item.id)
        savesRepository.toggleSave(item)
        _snackbarMessage.value = if (wasSaved) "Removed from Saved" else "Saved to Library!"
    }

    fun undoLastUnsave() {
        savesRepository.undoLastUnsave()
    }

    fun createCollection(name: String) {
        val col = savesRepository.createCollection(name)
        _snackbarMessage.value = "Collection '${col.name}' created!"
    }

    fun renameCollection(collectionId: String, newName: String) {
        savesRepository.renameCollection(collectionId, newName)
        _snackbarMessage.value = "Collection renamed"
    }

    fun deleteCollection(collectionId: String) {
        savesRepository.deleteCollection(collectionId)
        if (_selectedCollectionId.value == collectionId) {
            _selectedCollectionId.value = null
        }
        _snackbarMessage.value = "Collection deleted"
    }

    fun toggleMediaInCollection(collectionId: String, mediaItem: MediaItem) {
        savesRepository.toggleMediaInCollection(collectionId, mediaItem)
    }

    fun openAddToCollectionDialog(item: MediaItem) {
        _addToCollectionItem.value = item
    }

    fun closeAddToCollectionDialog() {
        _addToCollectionItem.value = null
    }

    fun selectCollection(collectionId: String) {
        _selectedCollectionId.value = collectionId
    }

    fun clearSelectedCollection() {
        _selectedCollectionId.value = null
    }

    fun getItemsForCollection(collectionId: String): List<MediaItem> {
        return savesRepository.getMediaForCollection(collectionId)
    }

    // ==========================================
    // HOME FEED METHODS (Completely isolated)
    // ==========================================

    fun setHomeSource(source: MediaSourceConfig) {
        val current = _homeState.value
        if (current.activeSource == source) return

        val supportedTypes = source.getSupportedMediaTypes()
        val sanitizedFilter = if (current.filterState.mediaType !in supportedTypes) {
            current.filterState.copy(mediaType = MediaType.ALL)
        } else {
            current.filterState
        }

        _homeState.value = current.copy(
            activeSource = source,
            filterState = sanitizedFilter,
            rawItems = emptyList(),
            mediaItems = emptyList(),
            currentPage = 1,
            hasReachedEnd = false
        )
        syncLegacyHomeState()
        loadHomeData(isRefresh = true)
    }

    fun setHomeFilterState(newFilters: FilterState) {
        val current = _homeState.value
        val supportedTypes = current.activeSource.getSupportedMediaTypes()
        val sanitizedFilters = if (newFilters.mediaType !in supportedTypes) {
            newFilters.copy(mediaType = MediaType.ALL)
        } else {
            newFilters
        }

        val oldEffectiveQuery = QueryBuilder.buildEffectiveQuery(
            userQuery = current.submittedQuery,
            source = current.activeSource,
            mediaType = current.filterState.mediaType,
            rating = current.filterState.rating
        )
        val newEffectiveQuery = QueryBuilder.buildEffectiveQuery(
            userQuery = current.submittedQuery,
            source = current.activeSource,
            mediaType = sanitizedFilters.mediaType,
            rating = sanitizedFilters.rating
        )

        val updatedItems = applyClientFilters(current.rawItems, sanitizedFilters)
        _homeState.value = current.copy(
            filterState = sanitizedFilters,
            mediaItems = updatedItems
        )
        syncLegacyHomeState()

        if (oldEffectiveQuery != newEffectiveQuery) {
            _homeState.value = _homeState.value.copy(
                rawItems = emptyList(),
                mediaItems = emptyList()
            )
            syncLegacyHomeState()
            loadHomeData(isRefresh = true)
        }
    }

    fun setHomeSourceAndFilter(source: MediaSourceConfig, filter: FilterState) {
        val current = _homeState.value
        val supportedTypes = source.getSupportedMediaTypes()
        val sanitizedFilter = if (filter.mediaType !in supportedTypes) {
            filter.copy(mediaType = MediaType.ALL)
        } else {
            filter
        }

        val sourceChanged = current.activeSource != source
        val filterChanged = current.filterState != sanitizedFilter

        if (!sourceChanged && !filterChanged) return

        _homeState.value = current.copy(
            activeSource = source,
            filterState = sanitizedFilter,
            rawItems = emptyList(),
            mediaItems = emptyList(),
            currentPage = 1,
            hasReachedEnd = false
        )
        syncLegacyHomeState()
        loadHomeData(isRefresh = true)
    }

    fun refreshHome() {
        if (_homeState.value.isRefreshing) return
        homeJob?.cancel()
        _homeState.value = _homeState.value.copy(
            isRefreshing = true,
            isLoading = false,
            isLoadingMore = false,
            errorMessage = null,
            paginationError = null
        )
        syncLegacyHomeState()
        loadHomeData(isRefresh = true)
    }

    fun loadNextHomePage() {
        val current = _homeState.value
        if (!current.hasReachedEnd && !current.isLoading && !current.isLoadingMore && !current.isRefreshing) {
            loadHomeData(isRefresh = false)
        }
    }

    private fun loadHomeData(isRefresh: Boolean) {
        loadFeedData(isHome = true, isRefresh = isRefresh)
    }

    private fun loadSearchData(isRefresh: Boolean) {
        loadFeedData(isHome = false, isRefresh = isRefresh)
    }

    private fun loadFeedData(isHome: Boolean, isRefresh: Boolean) {
        val stateFlow = if (isHome) _homeState else _searchState
        val current = stateFlow.value
        if (!isRefresh && (current.isLoading || current.isLoadingMore || current.isRefreshing ||
                    current.hasReachedEnd || current.paginationError != null)) return

        (if (isHome) homeJob else searchJob)?.cancel()
        stateFlow.value = current.copy(
            isLoading = isRefresh && !current.isRefreshing,
            isLoadingMore = !isRefresh,
            errorMessage = null,
            paginationError = null
        )
        if (isHome) syncLegacyHomeState()

        // Capture the request identity. Draft search edits never change an in-flight request.
        val source = current.activeSource
        val query = QueryBuilder.buildEffectiveQuery(
            current.submittedQuery, source, current.filterState.mediaType, current.filterState.rating
        )
        val newJob = viewModelScope.launch {
            var page = if (isRefresh) 1 else current.currentPage
            var rawItems = if (isRefresh) emptyList() else current.rawItems
            var randomItems = if (isRefresh) emptyList() else current.mediaItems

            // Bound automatic scanning when pages contain only duplicates or filtered-out items.
            for (attempt in 0 until 3) {
                val result = apiClient.fetchMedia(source, query, page)
                if (!isActive) return@launch
                val error = result.exceptionOrNull()
                if (error != null) {
                    if (error is CancellationException) throw error
                    val message = error.message ?: "Failed to connect to source."
                    val keepArtwork = stateFlow.value.mediaItems.isNotEmpty()
                    stateFlow.value = stateFlow.value.copy(
                        isLoading = false, isLoadingMore = false, isRefreshing = false,
                        errorMessage = if (isRefresh && !keepArtwork) message else null,
                        paginationError = if (!isRefresh) message else null
                    )
                    if (keepArtwork && isRefresh) _snackbarMessage.value = "Refresh failed: $message"
                    if (isHome) syncLegacyHomeState()
                    return@launch
                }

                val fetched = result.getOrThrow()
                val ids = rawItems.map { it.id }.toMutableSet()
                val urls = rawItems.map { it.actualMediaUrl }.filter { it.isNotBlank() }.toMutableSet()
                val uniqueItems = fetched.filter { item ->
                    if (item.id in ids || (item.actualMediaUrl.isNotBlank() && item.actualMediaUrl in urls)) {
                        false
                    } else {
                        ids.add(item.id)
                        if (item.actualMediaUrl.isNotBlank()) urls.add(item.actualMediaUrl)
                        true
                    }
                }
                rawItems = rawItems + uniqueItems
                // Read client filters again: orientation/sort may change while awaiting a page.
                val filters = stateFlow.value.filterState
                val visible = if (filters.sort == SortOption.RANDOM) {
                    randomItems = applyClientFilters(randomItems, filters) +
                        applyClientFilters(uniqueItems, filters).shuffled()
                    randomItems
                } else applyClientFilters(rawItems, filters)
                val exhausted = fetched.isEmpty()
                val duplicateLimit = attempt == 2 && uniqueItems.isEmpty()
                val finished = exhausted || duplicateLimit ||
                    (uniqueItems.isNotEmpty() && visible.isNotEmpty()) || attempt == 2
                stateFlow.value = stateFlow.value.copy(
                    rawItems = rawItems, mediaItems = visible, currentPage = page + 1,
                    hasReachedEnd = exhausted || duplicateLimit,
                    isLoading = !finished && isRefresh && !current.isRefreshing,
                    isLoadingMore = !finished && !isRefresh,
                    isRefreshing = !finished && current.isRefreshing,
                    errorMessage = null, paginationError = null
                )
                if (isHome) syncLegacyHomeState()
                if (finished) break
                page++
            }
        }
        if (isHome) homeJob = newJob else searchJob = newJob
    }

    fun retryHomePage() {
        _homeState.value = _homeState.value.copy(paginationError = null)
        loadNextHomePage()
    }

    fun retrySearchPage() {
        _searchState.value = _searchState.value.copy(paginationError = null)
        loadNextSearchPage()
    }

    // ==========================================
    // SEARCH METHODS (Completely isolated)
    // ==========================================

    fun setSearchSource(source: MediaSourceConfig) {
        val current = _searchState.value
        if (current.activeSource == source) return

        val supportedTypes = source.getSupportedMediaTypes()
        val sanitizedFilter = if (current.filterState.mediaType !in supportedTypes) {
            current.filterState.copy(mediaType = MediaType.ALL)
        } else {
            current.filterState
        }

        _searchState.value = current.copy(
            activeSource = source,
            filterState = sanitizedFilter,
            rawItems = emptyList(),
            mediaItems = emptyList(),
            currentPage = 1,
            hasReachedEnd = false
        )
        loadSearchData(isRefresh = true)
    }

    fun setSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(searchQuery = query)
    }

    fun executeSearchQuery() {
        _searchState.value = _searchState.value.copy(
            submittedQuery = _searchState.value.searchQuery.trim(),
            rawItems = emptyList(),
            mediaItems = emptyList(),
            currentPage = 1,
            hasReachedEnd = false
        )
        loadSearchData(isRefresh = true)
    }

    fun setSearchFilterState(newFilters: FilterState) {
        val current = _searchState.value
        val supportedTypes = current.activeSource.getSupportedMediaTypes()
        val sanitizedFilters = if (newFilters.mediaType !in supportedTypes) {
            newFilters.copy(mediaType = MediaType.ALL)
        } else {
            newFilters
        }

        val oldEffectiveQuery = QueryBuilder.buildEffectiveQuery(
            userQuery = current.submittedQuery,
            source = current.activeSource,
            mediaType = current.filterState.mediaType,
            rating = current.filterState.rating
        )
        val newEffectiveQuery = QueryBuilder.buildEffectiveQuery(
            userQuery = current.submittedQuery,
            source = current.activeSource,
            mediaType = sanitizedFilters.mediaType,
            rating = sanitizedFilters.rating
        )

        val updatedItems = applyClientFilters(current.rawItems, sanitizedFilters)
        _searchState.value = current.copy(
            filterState = sanitizedFilters,
            mediaItems = updatedItems
        )

        if (oldEffectiveQuery != newEffectiveQuery) {
            _searchState.value = _searchState.value.copy(
                rawItems = emptyList(),
                mediaItems = emptyList()
            )
            loadSearchData(isRefresh = true)
        }
    }

    fun refreshSearch() {
        if (_searchState.value.isRefreshing) return
        searchJob?.cancel()
        _searchState.value = _searchState.value.copy(
            isRefreshing = true,
            isLoading = false,
            isLoadingMore = false,
            errorMessage = null,
            paginationError = null
        )
        loadSearchData(isRefresh = true)
    }

    fun loadNextSearchPage() {
        val current = _searchState.value
        if (!current.hasReachedEnd && !current.isLoading && !current.isLoadingMore && !current.isRefreshing) {
            loadSearchData(isRefresh = false)
        }
    }

    // ==========================================
    // BACKWARD COMPATIBILITY ALIASES FOR TESTS
    // ==========================================

    fun setActiveSource(source: MediaSourceConfig) {
        setHomeSource(source)
    }

    fun setFilterState(newFilters: FilterState) {
        setHomeFilterState(newFilters)
    }

    fun executeSearch() {
        executeSearchQuery()
    }

    fun loadSourceData(
        source: MediaSourceConfig = _homeState.value.activeSource,
        query: String = _homeState.value.searchQuery,
        isRefresh: Boolean = false
    ) {
        if (source != _homeState.value.activeSource) {
            setHomeSource(source)
        } else {
            loadHomeData(isRefresh)
        }
    }

    fun loadNextPage() {
        loadNextHomePage()
    }

    // ==========================================
    // SOURCE MANAGEMENT & DIAGNOSTICS
    // ==========================================

    fun addCustomSource(source: MediaSourceConfig) {
        if (!repository.addSource(source)) {
            _snackbarMessage.value = "Could not securely save source credentials. Please try again."
            return
        }
        if (sourcePickerTarget == "search") {
            setSearchSource(source)
        } else {
            setHomeSource(source)
        }
        _isAddSourceOpen.value = false
        _editingSourceConfig.value = null
        _snackbarMessage.value = "Source '${source.name}' saved successfully!"
    }

    fun updateCustomSource(source: MediaSourceConfig) {
        if (!repository.updateSource(source)) {
            _snackbarMessage.value = "Could not securely save source credentials. Your source was not updated."
            return
        }
        if (_homeState.value.activeSource.id == source.id) {
            setHomeSource(source)
        }
        if (_searchState.value.activeSource.id == source.id) {
            setSearchSource(source)
        }
        _isAddSourceOpen.value = false
        _editingSourceConfig.value = null
        _snackbarMessage.value = "Source '${source.name}' updated."
    }

    fun deleteSource(sourceId: String) {
        if (!repository.deleteSource(sourceId)) {
            _snackbarMessage.value = "Could not save source changes. Please try again."
            return
        }
        val fallback = repository.sources.value.firstOrNull { it.isBuiltIn }
            ?: MediaSourceConfig.BUILT_IN_SAFEBOORU

        if (_homeState.value.activeSource.id == sourceId) {
            setHomeSource(fallback)
        }
        if (_searchState.value.activeSource.id == sourceId) {
            setSearchSource(fallback)
        }
        _snackbarMessage.value = "Source removed."
    }

    fun openAddSourceDialog(initialConfig: MediaSourceConfig? = null) {
        _editingSourceConfig.value = initialConfig
        _isAddSourceOpen.value = true
    }

    fun openEditSourceDialog(source: MediaSourceConfig) {
        dismissSourceDiagnostics()
        val configToEdit = if (source.isBuiltIn) {
            source.copy(
                id = "custom_${source.id}_${System.currentTimeMillis() % 10000}",
                name = "${source.name} (Custom)",
                isBuiltIn = false
            )
        } else {
            source
        }
        openAddSourceDialog(configToEdit)
    }

    fun closeAddSourceDialog() {
        _isAddSourceOpen.value = false
        _editingSourceConfig.value = null
    }

    fun openSourcePicker(target: String = "home") {
        sourcePickerTarget = target
        _isSourcePickerOpen.value = true
    }

    fun closeSourcePicker() {
        _isSourcePickerOpen.value = false
    }

    fun openSourceDiagnostics(source: MediaSourceConfig) {
        _diagnosticSource.value = source
        runDiagnosticsForSource(source)
    }

    fun runDiagnosticsForSource(source: MediaSourceConfig) {
        diagnosticJob?.cancel()
        _isDiagnosing.value = true
        _diagnosticReport.value = null
        diagnosticJob = viewModelScope.launch {
            try {
                val report = apiClient.diagnoseSource(source)
                if (isActive) _diagnosticReport.value = report
            } finally {
                if (isActive) _isDiagnosing.value = false
            }
        }
    }

    fun dismissSourceDiagnostics() {
        diagnosticJob?.cancel()
        _diagnosticSource.value = null
        _diagnosticReport.value = null
        _isDiagnosing.value = false
    }

    fun duplicateSource(source: MediaSourceConfig) {
        val newSource = source.copy(
            id = "custom_${System.currentTimeMillis()}_${(1000..9999).random()}",
            name = "${source.name} (Copy)",
            isBuiltIn = false
        )
        if (!repository.addSource(newSource)) {
            _snackbarMessage.value = "Could not securely copy source credentials. Please try again."
            return
        }
        _snackbarMessage.value = "Created copy of '${source.name}'"
    }

    fun exportSourceConfig(source: MediaSourceConfig): String {
        val json = source.toExportJson(sanitizeSecrets = true)
        _snackbarMessage.value = "Sanitized config for '${source.name}' copied to clipboard!"
        return json
    }

    // ==========================================
    // LIGHTBOX / VIEWER
    // ==========================================

    fun openLightbox(index: Int, items: List<MediaItem>) {
        _lightboxItems.value = items
        _lightboxIndex.value = index
    }

    fun openLightbox(index: Int) {
        openLightbox(index, _homeState.value.mediaItems)
    }

    fun closeLightbox() {
        _lightboxIndex.value = null
        _lightboxItems.value = emptyList()
    }

    fun setLightboxIndex(index: Int) {
        _lightboxIndex.value = index
    }

    fun nextLightboxItem() {
        val current = _lightboxIndex.value ?: return
        val count = _lightboxItems.value.size
        if (current < count - 1) {
            _lightboxIndex.value = current + 1
        }
    }

    fun previousLightboxItem() {
        val current = _lightboxIndex.value ?: return
        if (current > 0) {
            _lightboxIndex.value = current - 1
        }
    }

    // ==========================================
    // DOWNLOAD & PROMPT CONFIGURATION
    // ==========================================

    fun promptDownload(item: MediaItem) {
        _downloadTargetItem.value = item
    }

    fun dismissDownloadPrompt() {
        _downloadTargetItem.value = null
    }

    fun downloadImage(item: MediaItem) {
        promptDownload(item)
    }

    fun sanitizeFilename(name: String): String {
        val sanitized = name.replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
        return sanitized.take(100).trim()
    }

    fun resolveDownloadFilename(item: MediaItem, customFilename: String?): String {
        val extension = when (item.mediaType) {
            MediaType.GIF -> "gif"
            MediaType.VIDEO -> {
                val fromExt = item.fileExt?.lowercase()?.takeIf { it in listOf("mp4", "webm", "mkv", "mov", "avi") }
                val fromUrl = item.actualMediaUrl.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase().takeIf { it in listOf("mp4", "webm", "mkv", "mov", "avi") }
                fromExt ?: fromUrl ?: "mp4"
            }
            else -> {
                val fromExt = item.fileExt?.lowercase()?.takeIf { it in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }
                val fromUrl = item.actualMediaUrl.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase().takeIf { it in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }
                fromExt ?: fromUrl ?: "jpg"
            }
        }

        val cleanName = customFilename?.let { sanitizeFilename(it) }?.takeIf { it.isNotBlank() }
        return if (cleanName != null) {
            val knownExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mkv", "mov", "bmp", "avi")
            var baseName: String = cleanName
            for (ext in knownExtensions) {
                if (baseName.endsWith(".$ext", ignoreCase = true)) {
                    baseName = baseName.substring(0, baseName.length - (ext.length + 1))
                    break
                }
            }
            baseName = baseName.trim().trimEnd('.')
            if (baseName.isBlank()) {
                baseName = "media_${item.id}"
            }
            "$baseName.$extension"
        } else {
            "MediaBrowser_${System.currentTimeMillis()}.$extension"
        }
    }

    fun downloadMediaWithQuality(item: MediaItem, quality: DownloadQuality, customFilename: String? = null) {
        _downloadTargetItem.value = null
        try {
            val context = getApplication<Application>().applicationContext
            val targetUrl = item.getDownloadUrl(quality)
            val uri = Uri.parse(targetUrl)

            val destinationFilename = resolveDownloadFilename(item, customFilename)

            val request = DownloadManager.Request(uri).apply {
                setTitle("${item.title} [${quality.label}]")
                setDescription("Downloading from ${item.sourceName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    destinationFilename
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                val headers = ArtfluxNetwork.getHeadersForUrl(targetUrl)
                for ((k, v) in headers) {
                    addRequestHeader(k, v)
                }
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(request)
            val mediaLabel = when (item.mediaType) {
                MediaType.GIF -> "GIF"
                MediaType.VIDEO -> "video"
                else -> "image"
            }
            val qualityLabel = if (quality == DownloadQuality.ORIGINAL) "1080p" else quality.label
            _snackbarMessage.value = "Downloading $mediaLabel ($qualityLabel)..."
        } catch (e: Exception) {
            _snackbarMessage.value = "Download error: ${e.message}"
        }
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    private fun applyClientFilters(items: List<MediaItem>, filters: FilterState): List<MediaItem> {
        var result = items
        if (filters.rating != MediaRating.ALL) {
            result = result.filter { it.rating == filters.rating }
        }
        if (filters.mediaType != MediaType.ALL) {
            result = result.filter { item ->
                when (filters.mediaType) {
                    MediaType.IMAGE -> item.mediaType == MediaType.IMAGE
                    MediaType.GIF -> item.mediaType == MediaType.GIF
                    MediaType.VIDEO -> item.mediaType == MediaType.VIDEO
                    else -> true
                }
            }
        }
        if (filters.orientation != Orientation.ALL) {
            result = result.filter { item ->
                val w = item.width ?: 1
                val h = item.height ?: 1
                when (filters.orientation) {
                    Orientation.PORTRAIT -> h > w
                    Orientation.LANDSCAPE -> w > h
                    Orientation.SQUARE -> (w.toFloat() / h.toFloat()) in 0.9f..1.1f
                    else -> true
                }
            }
        }
        return when (filters.sort) {
            SortOption.LATEST -> result
            SortOption.POPULAR -> {
                val hasMetrics = result.any { (it.favorites ?: it.views ?: it.score) != null }
                if (hasMetrics) {
                    result.sortedByDescending { it.favorites ?: it.views ?: it.score ?: 0 }
                } else {
                    result
                }
            }
            SortOption.TOP_RATED -> {
                val hasScore = result.any { it.score != null }
                if (hasScore) {
                    result.sortedByDescending { it.score ?: 0 }
                } else {
                    result
                }
            }
            SortOption.RANDOM -> result
        }
    }
}
