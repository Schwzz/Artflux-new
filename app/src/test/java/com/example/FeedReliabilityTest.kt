package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaFetcher
import com.example.model.*
import com.example.ui.MediaBrowserViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedReliabilityTest {
    private val fetcher = ControlledFetcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun viewModel(): MediaBrowserViewModel {
        val vm = MediaBrowserViewModel(ApplicationProvider.getApplicationContext(), fetcher)
        stores += ViewModelStore().apply { put("feed", vm) }
        return vm
    }

    private fun item(id: String, score: Int = 0, width: Int = 100, height: Int = 200) = MediaItem(
        id = id, title = id, actualMediaUrl = "https://example.org/$id.jpg",
        previewUrl = "https://example.org/$id.jpg", score = score, width = width, height = height
    )

    @Test fun editedActiveSourceReplacesConfigWithoutChangingId() = runTest {
        val vm = viewModel()
        val source = MediaSourceConfig(id = "custom", name = "Old", apiUrl = "https://example.org/old")
        vm.addCustomSource(source)
        runCurrent()
        val edited = source.copy(name = "New", apiUrl = "https://example.org/new")
        vm.updateCustomSource(edited)
        runCurrent()
        assertEquals(edited, vm.homeState.value.activeSource)
        assertEquals(edited, fetcher.calls.last().source)
    }

    @Test fun paginationUsesSubmittedQueryWhileUserEditsDraft() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.setSearchQuery("cats")
        vm.executeSearchQuery()
        runCurrent()
        fetcher.calls.last().result.complete(Result.success(listOf(item("cat"))))
        runCurrent()
        vm.setSearchQuery("dogs")
        vm.loadNextSearchPage()
        runCurrent()
        assertEquals("cats", fetcher.calls.last().query)
        assertEquals(2, fetcher.calls.last().page)
        assertEquals("dogs", vm.searchState.value.searchQuery)
    }

    @Test fun responseUsesLatestClientFiltersAndSortsAcrossAllPages() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.setHomeFilterState(FilterState(orientation = Orientation.PORTRAIT, sort = SortOption.TOP_RATED))
        fetcher.calls[0].result.complete(Result.success(listOf(item("low", 1), item("high", 9), item("wide", 90, 200, 100))))
        runCurrent()
        assertEquals(listOf("high", "low"), vm.homeState.value.mediaItems.map { it.id })
        vm.loadNextHomePage()
        runCurrent()
        vm.setHomeFilterState(FilterState(orientation = Orientation.LANDSCAPE, sort = SortOption.TOP_RATED))
        fetcher.calls.last().result.complete(Result.success(listOf(item("best", 100, 200, 100))))
        runCurrent()
        assertEquals(listOf("best", "wide"), vm.homeState.value.mediaItems.map { it.id })
    }

    @Test fun duplicatesWithinFirstPageAreRemoved() = runTest {
        val vm = viewModel()
        runCurrent()
        val original = item("one")
        fetcher.calls[0].result.complete(Result.success(listOf(original, original.copy(id = "same-url"), original.copy(actualMediaUrl = "https://example.org/other.jpg"))))
        runCurrent()
        assertEquals(listOf(original), vm.homeState.value.rawItems)
    }

    @Test fun failedRefreshPreservesArtworkAndPagination() = runTest {
        val vm = viewModel()
        runCurrent()
        fetcher.calls[0].result.complete(Result.success(listOf(item("one"))))
        runCurrent()
        vm.refreshHome()
        runCurrent()
        fetcher.calls.last().result.complete(Result.failure(Exception("Offline")))
        runCurrent()
        assertEquals(listOf("one"), vm.homeState.value.mediaItems.map { it.id })
        assertEquals(2, vm.homeState.value.currentPage)
        assertNull(vm.homeState.value.errorMessage)
        assertFalse(vm.homeState.value.isRefreshing)
        assertTrue(vm.snackbarMessage.value!!.contains("Offline"))
    }

    @Test fun pageFailureWaitsForExplicitRetryAndKeepsPageNumber() = runTest {
        val vm = viewModel()
        runCurrent()
        fetcher.calls[0].result.complete(Result.success(listOf(item("one"))))
        runCurrent()
        vm.loadNextHomePage()
        runCurrent()
        fetcher.calls.last().result.complete(Result.failure(Exception("Offline")))
        runCurrent()
        val count = fetcher.calls.size
        vm.loadNextHomePage()
        runCurrent()
        assertEquals(count, fetcher.calls.size)
        assertEquals("Offline", vm.homeState.value.paginationError)
        vm.retryHomePage()
        runCurrent()
        assertEquals(count + 1, fetcher.calls.size)
        assertEquals(2, fetcher.calls.last().page)
    }

    @Test fun filteredFirstPageContinuesUntilVisibleArtwork() = runTest {
        val vm = viewModel()
        vm.setHomeFilterState(FilterState(orientation = Orientation.PORTRAIT))
        runCurrent()
        fetcher.calls[0].result.complete(Result.success(listOf(item("wide", width = 200, height = 100))))
        runCurrent()
        assertEquals(2, fetcher.calls.last().page)
        fetcher.calls.last().result.complete(Result.success(listOf(item("portrait"))))
        runCurrent()
        assertEquals(listOf("portrait"), vm.homeState.value.mediaItems.map { it.id })
        assertFalse(vm.homeState.value.isLoading)
    }

    @Test fun duplicateOnlyPaginationStopsAfterThreeAttempts() = runTest {
        val vm = viewModel()
        runCurrent()
        fetcher.calls[0].result.complete(Result.success(listOf(item("one"))))
        runCurrent()
        vm.loadNextHomePage()
        repeat(3) {
            runCurrent()
            fetcher.calls.last().result.complete(Result.success(listOf(item("one"))))
        }
        runCurrent()
        assertEquals(5, fetcher.calls.size) // two initial feeds plus three additional pages
        assertTrue(vm.homeState.value.hasReachedEnd)
        assertFalse(vm.homeState.value.isLoadingMore)
    }

    @Test fun cancelledSearchCannotPublishStaleResults() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.setSearchQuery("old")
        vm.executeSearchQuery()
        runCurrent()
        val old = fetcher.calls.last()
        vm.setSearchQuery("new")
        vm.executeSearchQuery()
        runCurrent()
        fetcher.calls.last().result.complete(Result.success(listOf(item("new"))))
        runCurrent()
        old.result.complete(Result.success(listOf(item("old"))))
        runCurrent()
        assertEquals(listOf("new"), vm.searchState.value.mediaItems.map { it.id })
    }

    @Test fun dismissedDiagnosticsCannotPublishLateReport() = runTest {
        val vm = viewModel()
        vm.openSourceDiagnostics(MediaSourceConfig.BUILT_IN_SAFEBOORU)
        runCurrent()
        vm.dismissSourceDiagnostics()
        fetcher.diagnostics.last().complete(SourceDiagnosticReport(true, "Late", emptyList()))
        runCurrent()
        assertNull(vm.diagnosticReport.value)
        assertNull(vm.diagnosticSource.value)
        assertFalse(vm.isDiagnosing.value)
    }

    private class ControlledFetcher : MediaFetcher {
        data class Call(val source: MediaSourceConfig, val query: String, val page: Int,
                        val result: CompletableDeferred<Result<List<MediaItem>>> = CompletableDeferred())
        val calls = mutableListOf<Call>()
        val diagnostics = mutableListOf<CompletableDeferred<SourceDiagnosticReport>>()
        override suspend fun fetchMedia(source: MediaSourceConfig, query: String, page: Int): Result<List<MediaItem>> {
            val call = Call(source, query, page)
            calls += call
            return call.result.await()
        }
        override suspend fun diagnoseSource(source: MediaSourceConfig): SourceDiagnosticReport {
            val result = CompletableDeferred<SourceDiagnosticReport>()
            diagnostics += result
            return result.await()
        }
    }
}
