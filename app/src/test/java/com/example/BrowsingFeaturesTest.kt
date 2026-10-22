package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.model.*
import com.example.ui.MediaBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class BrowsingFeaturesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun item(id: String, source: String = "source", tags: List<String> = emptyList()) = MediaItem(
        id, "Artwork $id", "https://example.org/$source/$id.jpg", "", sourceId = source, tags = tags)

    @Test fun blockingIsExactCaseInsensitivePersistentAndReversible() {
        val repo = BrowsingRepository(context)
        repo.block("  GORE ")
        assertTrue(repo.state.value.blocks(item("1", tags = listOf("GoRe"))))
        assertFalse(repo.state.value.blocks(item("2", tags = listOf("gore_warning"))))
        assertTrue(BrowsingRepository(context).state.value.blocks(item("3", tags = listOf("gore"))))
        repo.toggleBlock("gore")
        assertFalse(repo.state.value.blocks(item("1", tags = listOf("gore"))))
        repo.removeBlock("gore")
        assertTrue(repo.state.value.blockedTags.isEmpty())
    }

    @Test fun historyFavoritesSearchesAndSourcePositionsSurviveRestart() {
        val repo = BrowsingRepository(context)
        repeat(25) { repo.rememberQuery("query-$it") }
        repo.rememberQuery("query-10")
        repo.favorite("custom"); repo.saveSearch("custom", "sky", FilterState(rating = MediaRating.SAFE))
        repo.session("custom", SourceSession("sky", FilterState(sort = SortOption.TOP_RATED), 12, 34))
        val restored = BrowsingRepository(context).state.value
        assertEquals(20, restored.history.size)
        assertEquals("query-10", restored.history.first())
        assertTrue("custom" in restored.favorites)
        assertEquals("sky", restored.savedSearches.single().query)
        assertEquals(12, restored.sessions.getValue("custom").scrollIndex)
        assertEquals(34, restored.sessions.getValue("custom").scrollOffset)
        assertEquals(SortOption.TOP_RATED, restored.sessions.getValue("custom").filters.sort)
    }

    @Test fun tagSuggestionsExcludeBlockedTagsAndUseLastSearchToken() {
        val repo = BrowsingRepository(context)
        repo.rememberTags(listOf("sky", "skyline", "landscape"))
        repo.block("skyline")
        assertEquals(listOf("sky"), repo.state.value.suggestions("landscape sk"))
        assertTrue(repo.state.value.suggestions("-sk").isEmpty())
    }

    @Test fun sameProviderPostIdsFromDifferentSourcesCanBeSavedTogether() {
        val repo = SavesRepository(context)
        val one = item("123", "a"); val two = item("123", "b")
        val col = repo.createCollection("Both")
        repo.addItemsToCollection(col.id, listOf(one, two))
        val restored = SavesRepository(context)
        assertEquals(2, restored.savedItems.value.size)
        assertEquals(setOf(one.libraryKey, two.libraryKey), restored.getMediaForCollection(col.id).map { it.libraryKey }.toSet())
        repo.removeItems(setOf(one.libraryKey))
        assertEquals(listOf(two), repo.getMediaForCollection(col.id))
    }

    @Test fun removingFromACollectionKeepsSavedArtwork() {
        val repo = SavesRepository(context)
        val artwork = item("keep")
        val collection = repo.createCollection("Only membership")
        repo.addItemsToCollection(collection.id, listOf(artwork))
        repo.removeItemsFromCollection(collection.id, setOf(artwork.libraryKey))
        assertTrue(repo.getMediaForCollection(collection.id).isEmpty())
        assertTrue(repo.isSaved(artwork.libraryKey))
        assertEquals(listOf(artwork), SavesRepository(context).savedItems.value)
    }

    @Test fun legacySavedIdsAndCollectionReferencesMigrateTogether() {
        val old = item("42").copy(sourceId = "", sourceName = "Old source")
        val col = ArtCollection("col", "Legacy", mediaIds = listOf("42"))
        context.getSharedPreferences("artflux_saves_store", Context.MODE_PRIVATE).edit()
            .putString("saved_artworks_json", JSONArray().put(old.toJson()).toString())
            .putString("art_collections_json", JSONArray().put(col.toJson()).toString()).commit()
        val migrated = SavesRepository(context, mapOf("Old source" to "stable-source"))
        assertEquals("stable-source::42", migrated.savedItems.value.single().libraryKey)
        assertEquals("stable-source::42", migrated.collections.value.single().mediaIds.single())
        assertEquals(1, migrated.getMediaForCollection("col").size)
    }

    @Test fun backupRoundTripExcludesCredentialsIncludingUrlAuthentication() {
        val source = MediaSourceConfig(id = "private", name = "Private", apiUrl = "https://user:s3cr3t-123@example.org/feed?api_key=s3cr3t-123&page=dapi",
            apiKey = "s3cr3t-123", authHeaderValue = "s3cr3t-123", authQueryParams = listOf(AuthParam("signature", "s3cr3t-123")))
        val original = ArtfluxBackup(listOf(item("1")), listOf(ArtCollection("col", "Art", mediaIds = listOf(item("1").libraryKey))),
            listOf(source), BrowsingPreferences(blockedTags = listOf(BlockedTag("gore")), density = GridDensity.COMPACT),
            ColorPalette.CRIMSON_FLUX, ThumbnailQuality.Q720, true, false)
        val text = ArtfluxBackupCodec.encode(original)
        assertFalse(text.contains("s3cr3t-123"))
        val decoded = ArtfluxBackupCodec.decode(text)
        assertEquals(original.items, decoded.items)
        assertEquals(original.collections, decoded.collections)
        assertEquals(original.browsing, decoded.browsing)
        assertEquals(ColorPalette.CRIMSON_FLUX, decoded.palette)
        assertEquals("https://example.org/feed?page=dapi", decoded.sources.single().apiUrl)
        assertTrue(decoded.sources.single().apiKey.isEmpty())
    }

    @Test fun malformedOrUnsupportedBackupsAreRejectedBeforeImport() {
        for (text in listOf("invalid", "{}", "{\"format\":\"artflux-backup\",\"version\":99}")) {
            assertTrue(runCatching { ArtfluxBackupCodec.decode(text) }.isFailure)
        }
    }

    @Test fun missingProviderIdsAreStablePerArtworkAcrossPages() {
        val source = MediaSourceConfig(id = "custom", name = "Custom", apiUrl = "https://example.org", imageUrlField = "url")
        val one = MediaResponseParser.parseMediaItems(source, """[{"url":"https://example.org/one.jpg"}]""").single()
        val two = MediaResponseParser.parseMediaItems(source, """[{"url":"https://example.org/two.jpg"}]""").single()
        assertNotEquals(one.id, two.id)
        assertEquals(one.id, MediaResponseParser.parseMediaItems(source, """[{"url":"https://example.org/one.jpg"}]""").single().id)
    }

    @Test fun offlineQueuePersistsAndDoesNotQueueTheSameArtworkTwice() {
        val store = ArtworkDownloads(context)
        val first = store.enqueue(item("1"), true)
        assertEquals(first, store.enqueue(item("1"), true))
        val restored = ArtworkDownloads(context)
        assertEquals(first, restored.state.value.single().downloadId)
        assertTrue(restored.state.value.single().offline)
        assertNotEquals(first, restored.enqueue(item("1"), false))
    }

    @Test fun completedGalleryDownloadsCanBeDownloadedAgain() {
        val store = ArtworkDownloads(context)
        val first = store.enqueue(item("again"), false)
        val prefs = context.getSharedPreferences("artflux_downloads", Context.MODE_PRIVATE)
        val records = JSONArray(prefs.getString("records", "[]"))
        records.getJSONObject(0).put("status", android.app.DownloadManager.STATUS_SUCCESSFUL)
            .put("uri", "content://downloads/all_downloads/$first")
        prefs.edit().putString("records", records.toString()).commit()
        val restarted = ArtworkDownloads(context)
        assertNotEquals(first, restarted.enqueue(item("again"), false))
        assertEquals(2, restarted.state.value.size)
    }

    @Test fun homeSearchBlockingAndSavedSearchesUseRealViewModelPaths() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fetcher = object : MediaFetcher {
            override suspend fun fetchMedia(source: MediaSourceConfig, query: String, page: Int) = Result.success(
                if (page == 1) listOf(item("safe", tags = listOf("sky")), item("blocked", tags = listOf("gore"))) else emptyList())
            override suspend fun diagnoseSource(source: MediaSourceConfig) = SourceDiagnosticReport(true, summary = "OK")
        }
        val vm = MediaBrowserViewModel(context, fetcher)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            runCurrent()
            vm.searchHome("sky"); runCurrent(); vm.saveCurrentSearch()
            assertEquals("sky", vm.homeState.value.submittedQuery)
            assertEquals("sky", vm.browsing.value.history.first())
            assertEquals("sky", vm.browsing.value.savedSearches.single().query)
            vm.blockTag("gore")
            assertEquals(listOf("safe"), vm.homeState.value.mediaItems.map { it.id })
            vm.removeBlockedTag("gore")
            assertEquals(2, vm.homeState.value.mediaItems.size)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
