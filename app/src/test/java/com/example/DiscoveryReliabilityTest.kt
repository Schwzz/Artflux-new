package com.example

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.model.*
import com.example.ui.MediaBrowserViewModel
import com.example.ui.components.MediaLoadMessage
import com.example.ui.components.ViewerGeometry
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryReliabilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val source get() = MediaSourceConfig.BUILT_IN_SAFEBOORU
    private fun item(id: String, tags: List<String> = listOf("sky"), rating: MediaRating = MediaRating.SAFE) = MediaItem(
        id, "Artwork $id", "https://example.org/$id.jpg", "", tags = tags, rating = rating, sourceId = source.id, sourceName = source.name)

    @Test fun httpProblemsAreActionableAndRetryAfterIsBounded() {
        assertEquals(SourceCondition.AUTH_REQUIRED, SourceRequestException.http(401).condition)
        assertEquals(SourceCondition.ACCESS_DENIED, SourceRequestException.http(403).condition)
        assertEquals(SourceCondition.SERVER_ERROR, SourceRequestException.http(503).condition)
        assertEquals(SourceCondition.CONFIGURATION, SourceRequestException.http(404).condition)
        assertEquals(31_000L, SourceRequestException.http(429, "30", 1000).retryAt)
        assertEquals(61_000L, SourceRequestException.http(429, "garbage", 1000).retryAt)
        assertEquals(86_401_000L, SourceRequestException.http(429, "99999999", 1000).retryAt)
        assertEquals(60_000L, SourceRequestException.http(429, "Thu, 01 Jan 1970 00:01:00 GMT", 1000).retryAt)
    }

    @Test fun networkApiPreservesStatusWithoutLeakingResponseOrUrlSecrets() = runTest {
        val client = client(429, "sensitive-key", "30")
        try {
            val failure = MediaApiClient(client).fetchMedia(source.copy(apiUrl = "https://example.org?key=sensitive-key"), "", 1).exceptionOrNull() as SourceRequestException
            assertEquals(SourceCondition.RATE_LIMITED, failure.condition)
            assertTrue(failure.retryAt > System.currentTimeMillis())
            assertFalse(failure.message!!.contains("sensitive-key"))
            assertFalse(failure.message!!.contains("example.org"))
        } finally { close(client) }
    }

    @Test fun successfulHttpAuthAndBrokenMappingsAreNotReportedAsHealthyEmptyFeeds() = runTest {
        for ((body, expected) in listOf("{\"error\":\"invalid api key secret\"}" to SourceCondition.AUTH_REQUIRED,
            "{\"different\":[]}" to SourceCondition.CONFIGURATION, "[{\"wrong_image_field\":\"x\"}]" to SourceCondition.CONFIGURATION)) {
            val client = client(200, body)
            try {
                val failure = MediaApiClient(client).fetchMedia(source, "", 1).exceptionOrNull() as SourceRequestException
                assertEquals(expected, failure.condition)
            } finally { close(client) }
        }
        val client = client(200, "[]")
        try { assertTrue(MediaApiClient(client).fetchMedia(source, "", 1).getOrThrow().isEmpty()) } finally { close(client) }
    }

    @Test fun healthPersistsLastSuccessAndCooldownWithoutPersistingErrorText() {
        val repo = SourceHealthRepository(context)
        repo.success(source.id, 100)
        repo.failure(source.id, SourceRequestException(SourceCondition.RATE_LIMITED, 429, 1000), 200)
        val restored = SourceHealthRepository(context).state.value.getValue(source.id)
        assertEquals(100L, restored.lastSuccessAt)
        assertFalse(restored.canRetry(999))
        assertTrue(restored.canRetry(1000))
        repo.failure(source.id, IOException("secret-key-in-request"), 300)
        assertFalse(context.getSharedPreferences("artflux_source_health", Context.MODE_PRIVATE).getString("status", "")!!.contains("secret-key"))
        repo.success(source.id, 400)
        assertEquals(0L, repo.state.value.getValue(source.id).retryAt)
    }

    @Test fun offlineMetadataAndMissingStatePersistAndMissingCopiesCanBeRequeued() {
        val repo = ArtworkDownloads(context)
        val id = repo.enqueue(item("missing"), true)
        val prefs = context.getSharedPreferences("artflux_downloads", Context.MODE_PRIVATE)
        val records = JSONArray(prefs.getString("records", "[]"))
        records.getJSONObject(0).put("status", DownloadManager.STATUS_SUCCESSFUL).put("missing", true)
            .put("uri", "content://downloads/$id").put("bytes", 123L).put("total", 456L)
        prefs.edit().putString("records", records.toString()).commit()
        val restored = ArtworkDownloads(context)
        val missing = restored.state.value.single()
        assertFalse(missing.complete)
        assertEquals("File missing", missing.label)
        assertEquals(123L, missing.bytes)
        assertEquals(0L, missing.storedBytes)
        assertNull(restored.localItem(missing.item))
        assertNotEquals(id, restored.enqueue(missing.item, true))
    }

    @Test fun storageRemovalIsRestrictedToSelectedOfflineRecordsAndKeepsSavedCollections() {
        val repo = ArtworkDownloads(context)
        val one = item("one"); val two = item("two")
        val id = repo.enqueue(one, true)
        val other = repo.enqueue(two, true)
        val gallery = repo.enqueue(one, false)
        val saves = SavesRepository(context)
        val collection = saves.createCollection("Keep metadata")
        saves.addItemsToCollection(collection.id, listOf(one, two))
        assertEquals(1, repo.removeOffline(setOf(id, gallery, -99)))
        assertEquals(setOf(other, gallery), ArtworkDownloads(context).state.value.map { it.downloadId }.toSet())
        assertEquals(2, saves.getMediaForCollection(collection.id).size)
    }

    @Test fun storageReportsActualFilesRatherThanMetadataEstimates() {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        val file = File(directory, "storage-check.bin").apply { writeBytes(ByteArray(1024)) }
        try {
            val repo = ArtworkDownloads(context)
            repo.scanStorage()
            assertTrue(repo.storage.value.available)
            assertTrue(repo.storage.value.usedBytes >= 1024)
            assertTrue(repo.storage.value.fileCount >= 1)
        } finally { file.delete() }
    }

    @Test fun relatedRankingExcludesSeedBlockedAndDuplicateArtwork() {
        val seed = item("seed", listOf("sky", "clouds")).copy(author = "Artist")
        val a = item("a", listOf("sky", "clouds"))
        val b = item("b", listOf("sky"))
        val blocked = item("blocked", listOf("sky", "gore"))
        val unrelated = item("unrelated", listOf("forest"))
        val browsing = BrowsingPreferences(blockedTags = listOf(BlockedTag("gore")))
        assertEquals(listOf(a, b), RelatedArtwork.rank(seed, listOf(seed, b, blocked, unrelated, a, a, a.copy(id = "mirror", sourceId = "other")), browsing))
        assertEquals(listOf(a), RelatedArtwork.rank(seed, listOf(a, b), browsing, "clouds"))
        assertTrue(RelatedArtwork.sameArtist(seed, unrelated.copy(author = "artist")))
        assertNull(RelatedArtwork.artist(seed.copy(author = "12345")))
        assertEquals(listOf("clouds"), RelatedArtwork.tags(seed.copy(tags = listOf("clouds", "-sky", "rating:e", "{bad}", "gore")), browsing))
    }

    @Test fun relatedBrowsingRestoresViewerAndNeverChangesHomeSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val seed = item("seed").copy(author = "Artist")
        val match = item("match").copy(author = "artist")
        val blocked = item("blocked", listOf("sky", "gore"))
        val adult = item("adult", rating = MediaRating.ADULT)
        val vm = MediaBrowserViewModel(context, fetcher { _, query, _ -> Result.success(if (query.isEmpty()) listOf(seed, match) else listOf(seed, match, blocked, adult)) })
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent(); vm.blockTag("gore"); vm.openLightbox(0)
            vm.rememberSourceScroll(source.id, "", vm.homeState.value.filterState, 1, 37)
            val before = vm.homeState.value
            val session = vm.browsing.value.sessions[source.id]
            vm.openRelatedArtwork(seed); runCurrent()
            assertEquals(listOf(match), vm.relatedArtwork.value!!.remoteItems)
            assertEquals(before, vm.homeState.value)
            assertEquals(session, vm.browsing.value.sessions[source.id])
            vm.openRelatedResult(match)
            assertEquals(match.id, vm.lightboxItems.value[vm.lightboxIndex.value!!].id)
            assertFalse(vm.relatedArtwork.value!!.open)
            vm.closeLightbox()
            assertEquals(seed.id, vm.lightboxItems.value[vm.lightboxIndex.value!!].id)
            assertTrue(vm.relatedArtwork.value!!.open)
            vm.selectRelatedArtist(); runCurrent()
            assertTrue(vm.relatedArtwork.value!!.artistOnly)
            assertEquals(listOf(match), vm.relatedArtwork.value!!.localItems)
            assertTrue(vm.relatedArtwork.value!!.remoteItems.isEmpty())
            vm.dismissRelatedArtwork()
            assertEquals(before, vm.homeState.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun sourceHealthChecksAndCooldownDoNotMutateTheFeedOrSpamTheApi() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var calls = 0
        var rateLimited = true
        val vm = MediaBrowserViewModel(context, fetcher { _, _, _ -> calls++
            if (rateLimited) Result.failure(SourceRequestException(SourceCondition.RATE_LIMITED, 429, System.currentTimeMillis() + 60000)) else Result.success(listOf(item("ok"))) })
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent()
            assertEquals(SourceCondition.RATE_LIMITED, vm.sourceHealth.value[source.id]!!.condition)
            vm.refreshHome(); vm.checkSourceHealth(source); runCurrent()
            assertEquals(1, calls)
            // A fresh source can be checked independently without changing the Home state.
            val other = vm.sources.value.first { it.id != source.id }
            val before = vm.homeState.value
            rateLimited = false
            vm.checkSourceHealth(other); runCurrent()
            assertEquals(SourceCondition.HEALTHY, vm.sourceHealth.value[other.id]!!.condition)
            assertEquals(before, vm.homeState.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun changingRelatedTagCancelsObsoleteRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<List<MediaItem>>>()
        val seed = item("seed", listOf("sky", "forest"))
        val vm = MediaBrowserViewModel(context, fetcher { _, query, _ ->
            if (query.startsWith("sky")) pending.await() else Result.success(listOf(item("forest", listOf("forest")))) })
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent(); vm.openLightbox(0, listOf(seed)); vm.openRelatedArtwork(seed, "sky"); runCurrent()
            vm.selectRelatedTag("forest"); runCurrent()
            pending.complete(Result.success(listOf(item("stale"))))
            runCurrent()
            assertEquals("forest", vm.relatedArtwork.value!!.selectedTag)
            assertEquals(listOf("forest"), vm.relatedArtwork.value!!.remoteItems.map { it.id })
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun aNewSearchDuringCooldownCancelsTheOlderRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<List<MediaItem>>>()
        var calls = 0
        val vm = MediaBrowserViewModel(context, fetcher { _, _, _ ->
            calls++
            if (calls == 1) pending.await()
            else Result.failure(SourceRequestException(SourceCondition.RATE_LIMITED, 429, System.currentTimeMillis() + 60000))
        })
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent(); vm.checkSourceHealth(source); runCurrent()
            vm.searchHome("new-query"); runCurrent()
            pending.complete(Result.success(listOf(item("stale"))))
            runCurrent()
            assertEquals(2, calls)
            assertEquals("new-query", vm.homeState.value.submittedQuery)
            assertTrue(vm.homeState.value.rawItems.isEmpty())
            assertFalse(vm.homeState.value.isLoading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun openingCooldownDiagnosticsCancelsThePreviousSourcesReport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<SourceDiagnosticReport>()
        val api = object : MediaFetcher {
            override suspend fun fetchMedia(source: MediaSourceConfig, query: String, page: Int): Result<List<MediaItem>> =
                if (source.id == this@DiscoveryReliabilityTest.source.id) Result.success(emptyList())
                else Result.failure(SourceRequestException(SourceCondition.RATE_LIMITED, 429, System.currentTimeMillis() + 60000))
            override suspend fun diagnoseSource(source: MediaSourceConfig) = pending.await()
        }
        val vm = MediaBrowserViewModel(context, api)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent(); vm.openSourceDiagnostics(source); runCurrent()
            val other = vm.sources.value.first { it.id != source.id }
            vm.checkSourceHealth(other); runCurrent()
            vm.openSourceDiagnostics(other); runCurrent()
            pending.complete(SourceDiagnosticReport(true, summary = "Stale report")); runCurrent()
            assertEquals(other.id, vm.diagnosticSource.value!!.id)
            assertFalse(vm.isDiagnosing.value)
            assertFalse(vm.diagnosticReport.value!!.isSuccess)
            assertFalse(vm.diagnosticReport.value!!.summary.contains("Stale"))
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun zoomBoundsRespectFittedPortraitAndLandscapeImages() {
        val portrait = ViewerGeometry.clamp(1000f, 1000f, 2f, 400f, 400f, .5f)
        assertEquals(0f, portrait.x, .01f)
        assertEquals(200f, portrait.y, .01f)
        val landscape = ViewerGeometry.clamp(-1000f, -1000f, 2f, 400f, 400f, 2f)
        assertEquals(-200f, landscape.x, .01f)
        assertEquals(0f, landscape.y, .01f)
        assertEquals(0f, ViewerGeometry.clamp(100f, 100f, 1f, 400f, 400f, 1f).x, .01f)
        assertFalse(MediaLoadMessage.forError(IllegalStateException("https://host?key=secret"), false).contains("secret"))
    }

    private fun fetcher(block: suspend (MediaSourceConfig, String, Int) -> Result<List<MediaItem>>) = object : MediaFetcher {
        override suspend fun fetchMedia(source: MediaSourceConfig, query: String, page: Int) = block(source, query, page)
        override suspend fun diagnoseSource(source: MediaSourceConfig) = SourceDiagnosticReport(true, summary = "OK")
    }
    private fun client(code: Int, body: String, retry: String = "") = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Response")
            .header("Retry-After", retry).body(body.toResponseBody()).build()
    }.build()
    private fun close(client: OkHttpClient) { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
