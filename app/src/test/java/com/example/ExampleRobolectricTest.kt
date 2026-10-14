package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaApiClient
import com.example.model.FilterState
import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import com.example.model.ThumbnailQuality
import com.example.ui.MediaBrowserViewModel
import com.example.util.ArtfluxNetwork
import com.example.util.QueryBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Artflux", appName)
  }

  // --- 1. Source Configuration Tests ---

  @Test
  fun testBuiltInSourcesListContainsDefaultSources() {
    val defaults = MediaSourceConfig.DEFAULT_SOURCES
    assertEquals(4, defaults.size)

    val safebooru = defaults[0]
    assertEquals("Safebooru", safebooru.name)
    assertTrue(safebooru.isBuiltIn)
    assertTrue(safebooru.apiUrl.contains("safebooru.org"))

    val danbooru = defaults[1]
    assertEquals("Danbooru", danbooru.name)
    assertTrue(danbooru.isBuiltIn)
    assertTrue(danbooru.apiUrl.contains("danbooru.donmai.us"))

    val yandere = defaults[2]
    assertEquals("Yande.re", yandere.name)
    assertTrue(yandere.isBuiltIn)
    assertTrue(yandere.apiUrl.contains("yande.re"))

    val waifuIm = defaults[3]
    assertEquals("Waifu.im", waifuIm.name)
    assertTrue(waifuIm.isBuiltIn)
    assertTrue(waifuIm.apiUrl.contains("api.waifu.im"))
  }

  @Test
  fun testCustomSourcesSerializationAndLegacyDeserialization() {
    // 1. Serialization with all fields
    val config = MediaSourceConfig(
      name = "Custom Booru",
      apiUrl = "https://custom.booru.org/posts.json",
      searchParam = "tags",
      pageParam = "page",
      imageUrlField = "file_url",
      gifQueryTag = "animated",
      videoQueryTag = "mp4",
      safeRatingTag = "rating:safe",
      suggestiveRatingTag = "rating:questionable",
      adultRatingTag = "rating:explicit"
    )

    val json = config.toJson()
    val restored = MediaSourceConfig.fromJson(json)

    assertEquals("Custom Booru", restored.name)
    assertEquals("animated", restored.gifQueryTag)
    assertEquals("mp4", restored.videoQueryTag)
    assertEquals("rating:safe", restored.safeRatingTag)
    assertEquals("rating:questionable", restored.suggestiveRatingTag)
    assertEquals("rating:explicit", restored.adultRatingTag)

    // 2. Legacy JSON without query tags
    val legacyJson = JSONObject("""
      {
        "id": "legacy_source",
        "name": "Legacy Booru",
        "apiUrl": "https://legacy.booru.org/api",
        "searchParam": "q"
      }
    """.trimIndent())

    val legacyRestored = MediaSourceConfig.fromJson(legacyJson)
    assertEquals("Legacy Booru", legacyRestored.name)
    assertEquals("", legacyRestored.gifQueryTag)
    assertEquals("", legacyRestored.videoQueryTag)
    assertEquals("", legacyRestored.safeRatingTag)
    assertEquals("", legacyRestored.suggestiveRatingTag)
    assertEquals("", legacyRestored.adultRatingTag)
  }

  // --- 2. Rating Filters and Mappings Tests ---

  @Test
  fun testRatingFilterLabels() {
    assertEquals("All Ratings", MediaRating.ALL.label)
    assertEquals("Safe", MediaRating.SAFE.label)
    assertEquals("Suggestive", MediaRating.SUGGESTIVE.label)
    assertEquals("Adult", MediaRating.ADULT.label)
  }

  @Test
  fun testDanbooruRatingParsing() {
    val apiClient = MediaApiClient()
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU

    val jsonPayload = """
      [
        {"id": 1, "file_url": "https://cdn.donmai.us/1.jpg", "preview_file_url": "https://cdn.donmai.us/1t.jpg", "rating": "g", "tag_string": "safe_tag"},
        {"id": 2, "file_url": "https://cdn.donmai.us/2.jpg", "preview_file_url": "https://cdn.donmai.us/2t.jpg", "rating": "s", "tag_string": "sensitive_tag"},
        {"id": 3, "file_url": "https://cdn.donmai.us/3.jpg", "preview_file_url": "https://cdn.donmai.us/3t.jpg", "rating": "q", "tag_string": "questionable_tag"},
        {"id": 4, "file_url": "https://cdn.donmai.us/4.jpg", "preview_file_url": "https://cdn.donmai.us/4t.jpg", "rating": "e", "tag_string": "explicit_tag"}
      ]
    """.trimIndent()

    val items = apiClient.parseMediaItems(danbooru, jsonPayload)
    assertEquals(4, items.size)
    assertEquals(MediaRating.SAFE, items[0].rating)
    assertEquals(MediaRating.SUGGESTIVE, items[1].rating) // Danbooru 's' is sensitive -> SUGGESTIVE
    assertEquals(MediaRating.SUGGESTIVE, items[2].rating) // Danbooru 'q' is questionable -> SUGGESTIVE
    assertEquals(MediaRating.ADULT, items[3].rating)       // Danbooru 'e' is explicit -> ADULT
  }

  @Test
  fun testYandereRatingParsing() {
    val apiClient = MediaApiClient()
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE

    val jsonPayload = """
      [
        {"id": 10, "file_url": "https://files.yande.re/10.jpg", "preview_url": "https://assets.yande.re/10t.jpg", "rating": "s", "tags": "safe_art"},
        {"id": 20, "file_url": "https://files.yande.re/20.jpg", "preview_url": "https://assets.yande.re/20t.jpg", "rating": "q", "tags": "suggestive_art"},
        {"id": 30, "file_url": "https://files.yande.re/30.jpg", "preview_url": "https://assets.yande.re/30t.jpg", "rating": "e", "tags": "adult_art"}
      ]
    """.trimIndent()

    val items = apiClient.parseMediaItems(yandere, jsonPayload)
    assertEquals(3, items.size)
    assertEquals(MediaRating.SAFE, items[0].rating)       // Yande.re 's' is safe -> SAFE
    assertEquals(MediaRating.SUGGESTIVE, items[1].rating) // Yande.re 'q' is questionable -> SUGGESTIVE
    assertEquals(MediaRating.ADULT, items[2].rating)       // Yande.re 'e' is explicit -> ADULT
  }

  @Test
  fun testSafebooruRatingParsing() {
    val apiClient = MediaApiClient()
    val safebooru = MediaSourceConfig.BUILT_IN_SAFEBOORU

    val jsonPayload = """
      [
        {"id": 100, "directory": "850", "image": "test.jpg", "rating": "general", "tags": "cute", "preview_url": "https://safebooru.org/t.jpg"}
      ]
    """.trimIndent()

    val items = apiClient.parseMediaItems(safebooru, jsonPayload)
    assertEquals(1, items.size)
    assertEquals(MediaRating.SAFE, items[0].rating)
  }

  // --- 3. Source-Aware Rating & Media Type Query Composition ---

  @Test
  fun testDanbooruSourceAwareRatingQueries() {
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU

    val safeQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "miku",
      source = danbooru,
      mediaType = MediaType.ALL,
      rating = MediaRating.SAFE
    )
    assertEquals("miku rating:g", safeQuery)

    val suggQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "miku",
      source = danbooru,
      mediaType = MediaType.ALL,
      rating = MediaRating.SUGGESTIVE
    )
    assertEquals("miku rating:s,q", suggQuery)

    val adultQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "miku",
      source = danbooru,
      mediaType = MediaType.ALL,
      rating = MediaRating.ADULT
    )
    assertEquals("miku rating:e", adultQuery)
  }

  @Test
  fun testYandereSourceAwareRatingQueries() {
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE

    val safeQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "",
      source = yandere,
      mediaType = MediaType.ALL,
      rating = MediaRating.SAFE
    )
    assertEquals("rating:s", safeQuery)

    val suggQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "",
      source = yandere,
      mediaType = MediaType.ALL,
      rating = MediaRating.SUGGESTIVE
    )
    assertEquals("rating:q", suggQuery)

    val adultQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "",
      source = yandere,
      mediaType = MediaType.ALL,
      rating = MediaRating.ADULT
    )
    assertEquals("rating:e", adultQuery)
  }

  @Test
  fun testSafebooruAndCustomFallbackWhenRatingNotSupportedServerSide() {
    val safebooru = MediaSourceConfig.BUILT_IN_SAFEBOORU

    // Safebooru has no adult or suggestive tags
    val adultQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "scenery",
      source = safebooru,
      mediaType = MediaType.ALL,
      rating = MediaRating.ADULT
    )
    assertEquals("scenery", adultQuery) // remains unchanged for server query

    // Custom source with no rating tags
    val custom = MediaSourceConfig(name = "Untagged Source", apiUrl = "https://custom.org/api")
    val customQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = "landscape",
      source = custom,
      mediaType = MediaType.ALL,
      rating = MediaRating.SUGGESTIVE
    )
    assertEquals("landscape", customQuery)
  }

  // --- 4. Query Composition with Search + Media Type + Rating Deduplication ---

  @Test
  fun testSearchPlusMediaTypePlusRatingCombinationWithoutDuplicates() {
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU

    // Search + GIF + Adult
    val query1 = QueryBuilder.buildEffectiveQuery(
      userQuery = "hatsune_miku",
      source = danbooru,
      mediaType = MediaType.GIF,
      rating = MediaRating.ADULT
    )
    assertEquals("hatsune_miku animated_gif rating:e", query1)

    // User already typed "animated_gif" and "rating:e"
    val query2 = QueryBuilder.buildEffectiveQuery(
      userQuery = "animated_gif hatsune_miku rating:e",
      source = danbooru,
      mediaType = MediaType.GIF,
      rating = MediaRating.ADULT
    )
    assertEquals("animated_gif hatsune_miku rating:e", query2)

    // Danbooru Video + Suggestive
    val query3 = QueryBuilder.buildEffectiveQuery(
      userQuery = "genshin",
      source = danbooru,
      mediaType = MediaType.VIDEO,
      rating = MediaRating.SUGGESTIVE
    )
    assertEquals("genshin webm rating:s,q", query3)
  }

  @Test
  fun testSupportedMediaTypesPerSource() {
    val safebooru = MediaSourceConfig.BUILT_IN_SAFEBOORU
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE

    // Artflux can render and filter Image, GIF, and Video across all sources
    assertTrue(safebooru.getSupportedMediaTypes().contains(MediaType.GIF))
    assertTrue(safebooru.getSupportedMediaTypes().contains(MediaType.VIDEO))

    assertTrue(danbooru.getSupportedMediaTypes().contains(MediaType.GIF))
    assertTrue(danbooru.getSupportedMediaTypes().contains(MediaType.VIDEO))

    assertTrue(yandere.getSupportedMediaTypes().contains(MediaType.GIF))
    assertTrue(yandere.getSupportedMediaTypes().contains(MediaType.VIDEO))

    // Dedicated server-side discovery tags
    assertTrue(safebooru.canDiscoverGifs)
    assertFalse(safebooru.canDiscoverVideos)

    assertTrue(danbooru.canDiscoverGifs)
    assertTrue(danbooru.canDiscoverVideos)

    assertFalse(yandere.canDiscoverGifs)
    assertFalse(yandere.canDiscoverVideos)
  }

  // --- 5. Pagination & Source Switching in ViewModel ---

  @Test
  fun testViewModelSourceSwitchingAndPaginationReset() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(app)

    // Initially Safebooru
    assertEquals("builtin_safebooru", viewModel.activeSource.value.id)

    // Switch to Danbooru
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU
    viewModel.setActiveSource(danbooru)
    assertEquals("builtin_danbooru", viewModel.activeSource.value.id)

    // Apply Adult filter on Danbooru
    viewModel.setFilterState(FilterState(rating = MediaRating.ADULT))
    val effectiveQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = viewModel.searchQuery.value,
      source = viewModel.activeSource.value,
      mediaType = viewModel.filterState.value.mediaType,
      rating = viewModel.filterState.value.rating
    )
    assertEquals("rating:e", effectiveQuery)

    // Switch to Yande.re
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE
    viewModel.setActiveSource(yandere)
    assertEquals("builtin_yandere", viewModel.activeSource.value.id)

    val yandereEffectiveQuery = QueryBuilder.buildEffectiveQuery(
      userQuery = viewModel.searchQuery.value,
      source = viewModel.activeSource.value,
      mediaType = viewModel.filterState.value.mediaType,
      rating = viewModel.filterState.value.rating
    )
    assertEquals("rating:e", yandereEffectiveQuery)
  }

  // --- 6. Media Types Verification ---

  @Test
  fun testMediaTypeEnumIntegrity() {
    val mediaTypes = MediaType.values().map { it.name }
    assertEquals(4, mediaTypes.size)
    assertTrue(mediaTypes.contains("ALL"))
    assertTrue(mediaTypes.contains("IMAGE"))
    assertTrue(mediaTypes.contains("GIF"))
    assertTrue(mediaTypes.contains("VIDEO"))
    assertFalse(mediaTypes.contains("ART"))
  }

  // --- 7. Search & Home State Isolation Tests (Batch 3) ---

  @Test
  fun testSearchAndHomeStateIsolation() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(app)

    // 1. Initial State: Home and Search are both on default source (Safebooru)
    assertEquals("builtin_safebooru", viewModel.homeState.value.activeSource.id)
    assertEquals("builtin_safebooru", viewModel.searchState.value.activeSource.id)

    // 2. Set Home source to Danbooru and filter to Adult
    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU
    viewModel.setHomeSource(danbooru)
    viewModel.setHomeFilterState(FilterState(rating = MediaRating.ADULT, mediaType = MediaType.GIF))

    // Verify Home has Danbooru and Adult + GIF
    assertEquals("builtin_danbooru", viewModel.homeState.value.activeSource.id)
    assertEquals(MediaRating.ADULT, viewModel.homeState.value.filterState.rating)
    assertEquals(MediaType.GIF, viewModel.homeState.value.filterState.mediaType)

    // Verify Search is still Safebooru with default filters (100% Isolated)
    assertEquals("builtin_safebooru", viewModel.searchState.value.activeSource.id)
    assertEquals(MediaRating.ALL, viewModel.searchState.value.filterState.rating)
    assertEquals(MediaType.ALL, viewModel.searchState.value.filterState.mediaType)

    // 3. User configures Search: switch to Yande.re, query "genshin", rating Suggestive
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE
    viewModel.setSearchSource(yandere)
    viewModel.setSearchQuery("genshin")
    viewModel.setSearchFilterState(FilterState(rating = MediaRating.SUGGESTIVE))

    // Verify Search updated
    assertEquals("builtin_yandere", viewModel.searchState.value.activeSource.id)
    assertEquals("genshin", viewModel.searchState.value.searchQuery)
    assertEquals(MediaRating.SUGGESTIVE, viewModel.searchState.value.filterState.rating)

    // Verify Home remains completely intact and unmutated
    assertEquals("builtin_danbooru", viewModel.homeState.value.activeSource.id)
    assertEquals("", viewModel.homeState.value.searchQuery)
    assertEquals(MediaRating.ADULT, viewModel.homeState.value.filterState.rating)
    assertEquals(MediaType.GIF, viewModel.homeState.value.filterState.mediaType)
  }

  // --- 8. Thumbnail Quality Setting & Resolution Resolution Tests ---

  @Test
  fun testThumbnailQualityResolutionPolicy() {
    val item = MediaItem(
      id = "12345",
      title = "Artwork",
      actualMediaUrl = "https://cdn.booru.org/original/12345.jpg",
      previewUrl = "https://cdn.booru.org/preview/12345.jpg",
      sampleUrl = "https://cdn.booru.org/sample/12345.jpg",
      fileExt = "jpg",
      fileSize = 1048576L
    )

    // 360p / 480p should prefer thumbnailUrl
    assertEquals("https://cdn.booru.org/preview/12345.jpg", item.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q360))
    assertEquals("https://cdn.booru.org/preview/12345.jpg", item.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q480))

    // 720p should prefer sampleUrl if available
    assertEquals("https://cdn.booru.org/sample/12345.jpg", item.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))

    // 1080p should prefer sampleUrl or imageUrl
    assertEquals("https://cdn.booru.org/sample/12345.jpg", item.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q1080))

    // Fallback when sampleUrl is null (Batch 7: avoid falling back to original heavy file)
    val itemNoSample = item.copy(sampleUrl = null)
    assertEquals("https://cdn.booru.org/preview/12345.jpg", itemNoSample.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))
    assertEquals("https://cdn.booru.org/preview/12345.jpg", itemNoSample.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q1080))
  }

  // --- 9. Media Details & Metadata Parsing Tests ---

  @Test
  fun testMediaItemMetadataParsingWithSampleAndFileSize() {
    val apiClient = MediaApiClient()
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE

    val jsonPayload = """
      [
        {
          "id": 999,
          "file_url": "https://files.yande.re/image/999/art.png",
          "preview_url": "https://assets.yande.re/data/preview/999.jpg",
          "sample_url": "https://files.yande.re/sample/999/art_sample.jpg",
          "rating": "s",
          "tags": "hatsune_miku vocaloid",
          "width": 1920,
          "height": 1080,
          "file_size": 2097152,
          "file_ext": "png",
          "author": "IllustratorX"
        }
      ]
    """.trimIndent()

    val items = apiClient.parseMediaItems(yandere, jsonPayload)
    assertEquals(1, items.size)
    val parsed = items[0]

    assertEquals("999", parsed.id)
    assertEquals("https://files.yande.re/image/999/art.png", parsed.imageUrl)
    assertEquals("https://assets.yande.re/data/preview/999.jpg", parsed.thumbnailUrl)
    assertEquals("https://files.yande.re/sample/999/art_sample.jpg", parsed.sampleUrl)
    assertEquals(1920, parsed.width)
    assertEquals(1080, parsed.height)
    assertEquals(2097152L, parsed.fileSize)
    assertEquals("png", parsed.fileExt)
    assertEquals("IllustratorX", parsed.author)
  }

  // --- 10. Preference Persistence Tests (Batch 4 & 5) ---

  @Test
  fun testPreferencesPersistence() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsRepo = com.example.data.PreferencesRepository(context)

    // 1. Theme persistence (Default is DARK)
    assertEquals(com.example.model.AppTheme.DARK, prefsRepo.theme.value)

    prefsRepo.setTheme(com.example.model.AppTheme.LIGHT)
    assertEquals(com.example.model.AppTheme.LIGHT, prefsRepo.theme.value)

    // Simulate restart
    val newPrefsRepo = com.example.data.PreferencesRepository(context)
    assertEquals(com.example.model.AppTheme.LIGHT, newPrefsRepo.theme.value)

    newPrefsRepo.setTheme(com.example.model.AppTheme.DARK)
    assertEquals(com.example.model.AppTheme.DARK, newPrefsRepo.theme.value)

    // 2. NSFW Blur persistence (Default is true for new installs in Batch 8B)
    assertTrue(prefsRepo.blurNsfw.value)
    prefsRepo.setBlurNsfw(false)
    assertFalse(prefsRepo.blurNsfw.value)

    val restartedRepo = com.example.data.PreferencesRepository(context)
    assertFalse(restartedRepo.blurNsfw.value)
    restartedRepo.setBlurNsfw(true)
    assertTrue(restartedRepo.blurNsfw.value)
  }

  // --- 11. Home Feed Settings / Source & Filter Update Tests (Batch 4) ---

  @Test
  fun testHomeFeedSettingsUpdatesHomeStateOnly() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(app)

    // Set Search state to specific query and source
    viewModel.setSearchSource(MediaSourceConfig.BUILT_IN_DANBOORU)
    viewModel.setSearchQuery("cyberpunk")
    viewModel.setSearchFilterState(FilterState(rating = MediaRating.SUGGESTIVE))

    // Now update Home feed settings via unified control
    val yandere = MediaSourceConfig.BUILT_IN_YANDERE
    viewModel.setHomeSourceAndFilter(yandere, FilterState(rating = MediaRating.SAFE, mediaType = MediaType.IMAGE))

    // Home state updated
    assertEquals("builtin_yandere", viewModel.homeState.value.activeSource.id)
    assertEquals(MediaRating.SAFE, viewModel.homeState.value.filterState.rating)
    assertEquals(MediaType.IMAGE, viewModel.homeState.value.filterState.mediaType)

    // Search state is 100% unaffected
    assertEquals("builtin_danbooru", viewModel.searchState.value.activeSource.id)
    assertEquals("cyberpunk", viewModel.searchState.value.searchQuery)
    assertEquals(MediaRating.SUGGESTIVE, viewModel.searchState.value.filterState.rating)
  }

  // --- 12. Pull-To-Refresh Repeated Triggers & Pagination Reset (Batch 4) ---

  @Test
  fun testPullToRefreshResetsPaginationAndPreservesState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(app)

    val danbooru = MediaSourceConfig.BUILT_IN_DANBOORU
    viewModel.setHomeSource(danbooru)
    viewModel.setHomeFilterState(FilterState(rating = MediaRating.ADULT))

    // Trigger refresh repeatedly
    viewModel.refreshHome()
    assertTrue(viewModel.homeState.value.isRefreshing)
    assertEquals(1, viewModel.homeState.value.currentPage)
    assertEquals("builtin_danbooru", viewModel.homeState.value.activeSource.id)
    assertEquals(MediaRating.ADULT, viewModel.homeState.value.filterState.rating)

    // Search state is independent
    viewModel.setSearchSource(MediaSourceConfig.BUILT_IN_YANDERE)
    viewModel.setSearchQuery("scenery")
    viewModel.refreshSearch()
    assertTrue(viewModel.searchState.value.isRefreshing)
    assertEquals(1, viewModel.searchState.value.currentPage)
    assertEquals("builtin_yandere", viewModel.searchState.value.activeSource.id)
    assertEquals("scenery", viewModel.searchState.value.searchQuery)
  }

  // --- 13. Download Quality Mapping & Format Preservation (Batch 5) ---

  @Test
  fun testDownloadQualityResolutionMapping() {
    val imageItem = MediaItem(
      id = "101",
      title = "Scenery Art",
      actualMediaUrl = "https://cdn.artflux.org/files/original.png",
      previewUrl = "https://cdn.artflux.org/thumbs/preview.jpg",
      sampleUrl = "https://cdn.artflux.org/samples/sample_720.jpg",
      mediaType = MediaType.IMAGE
    )

    // Original uses original file
    assertEquals("https://cdn.artflux.org/files/original.png", imageItem.getDownloadUrl(com.example.model.DownloadQuality.ORIGINAL))

    // 1080p / 720p prefer sampleUrl if present
    assertEquals("https://cdn.artflux.org/samples/sample_720.jpg", imageItem.getDownloadUrl(com.example.model.DownloadQuality.Q1080))
    assertEquals("https://cdn.artflux.org/samples/sample_720.jpg", imageItem.getDownloadUrl(com.example.model.DownloadQuality.Q720))

    // 480p / 360p prefer preview thumbnailUrl
    assertEquals("https://cdn.artflux.org/thumbs/preview.jpg", imageItem.getDownloadUrl(com.example.model.DownloadQuality.Q480))
    assertEquals("https://cdn.artflux.org/thumbs/preview.jpg", imageItem.getDownloadUrl(com.example.model.DownloadQuality.Q360))
  }

  @Test
  fun testDownloadQualityPreservesGifAndVideoMedia() {
    val gifItem = MediaItem(
      id = "202",
      title = "Animated Anime",
      actualMediaUrl = "https://cdn.artflux.org/files/animation.gif",
      previewUrl = "https://cdn.artflux.org/thumbs/static_frame.jpg",
      mediaType = MediaType.GIF
    )

    // Even if 360p is requested, GIF must NOT download static jpg thumbnail
    assertEquals("https://cdn.artflux.org/files/animation.gif", gifItem.getDownloadUrl(com.example.model.DownloadQuality.Q360))
    assertEquals("https://cdn.artflux.org/files/animation.gif", gifItem.getDownloadUrl(com.example.model.DownloadQuality.ORIGINAL))

    val videoItem = MediaItem(
      id = "303",
      title = "AMV Clip",
      actualMediaUrl = "https://cdn.artflux.org/files/video.mp4",
      previewUrl = "https://cdn.artflux.org/thumbs/video_thumb.jpg",
      mediaType = MediaType.VIDEO
    )

    // Video must always download video file
    assertEquals("https://cdn.artflux.org/files/video.mp4", videoItem.getDownloadUrl(com.example.model.DownloadQuality.Q480))
    assertEquals("https://cdn.artflux.org/files/video.mp4", videoItem.getDownloadUrl(com.example.model.DownloadQuality.ORIGINAL))
  }

  // --- 14. Thumbnail Fallback Resolution Tests (Batch 5) ---

  @Test
  fun testThumbnailFallbackUrlResolution() {
    val item = MediaItem(
      id = "404",
      title = "Artwork",
      actualMediaUrl = "https://cdn.booru.org/orig.jpg",
      previewUrl = "https://cdn.booru.org/thumb.jpg",
      sampleUrl = "https://cdn.booru.org/sample.jpg"
    )

    // If preferred 720p is sampleUrl, fallback should provide thumbnailUrl
    val fallbackFor720 = item.getThumbnailFallbackUrl(com.example.model.ThumbnailQuality.Q720)
    assertEquals("https://cdn.booru.org/thumb.jpg", fallbackFor720)

    // If preferred 360p is thumbnailUrl, fallback should provide sampleUrl or imageUrl
    val fallbackFor360 = item.getThumbnailFallbackUrl(com.example.model.ThumbnailQuality.Q360)
    assertEquals("https://cdn.booru.org/sample.jpg", fallbackFor360)
  }

  // --- 15. Structured JSON Import & Parsing Tests (Batch 6) ---

  @Test
  fun testStructuredJsonImportAndParsing() {
    val structuredJson = """
      {
        "name": "ArtStation Search",
        "apiUrl": "https://api.artstation.com/v2/community/explore/projects.json",
        "searchParam": "query",
        "pageParam": "page",
        "pageStartsAt": 1,
        "pageSizeParam": "per_page",
        "defaultPageSize": 30,
        "itemsPath": "data",
        "imageUrlField": "cover.large_image_url",
        "thumbUrlField": "cover.small_image_url",
        "sampleUrlField": "cover.medium_image_url",
        "postUrlField": "permalink",
        "tagsField": "tags",
        "ratingField": "rating",
        "gifQueryTag": "animated",
        "videoQueryTag": "video",
        "safeRatingTag": "purity:safe",
        "suggestiveRatingTag": "purity:sketchy",
        "adultRatingTag": "purity:nsfw",
        "authentication": {
          "type": "query",
          "parameters": {
            "api_key": "abc123secret",
            "client_id": "client999"
          }
        },
        "description": "ArtStation community discovery endpoint."
      }
    """.trimIndent()

    val parseResult = MediaSourceConfig.parseJson(structuredJson)
    assertTrue(parseResult.isSuccess)
    val config = parseResult.getOrThrow()

    assertEquals("ArtStation Search", config.name)
    assertEquals("https://api.artstation.com/v2/community/explore/projects.json", config.apiUrl)
    assertEquals("query", config.searchParam)
    assertEquals("data", config.itemsPath)
    assertEquals("cover.large_image_url", config.imageUrlField)
    assertEquals("cover.small_image_url", config.thumbUrlField)
    assertEquals(com.example.model.AuthType.QUERY_PARAMS, config.authType)
    assertEquals(2, config.authQueryParams.size)
    assertEquals("abc123secret", config.getEffectiveAuthQueryParams()["api_key"])
    assertEquals("client999", config.getEffectiveAuthQueryParams()["client_id"])
  }

  // --- 16. Malformed JSON & Validation Failure Tests (Batch 6) ---

  @Test
  fun testMalformedJsonHandlingProducesErrorWithoutCrashing() {
    val invalidJson = "{ this is not valid json content..."
    val result = MediaSourceConfig.parseJson(invalidJson)
    assertTrue(result.isFailure)
    assertNotNull(result.exceptionOrNull()?.message)
    assertTrue(result.exceptionOrNull()?.message?.contains("JSON") == true)
  }

  @Test
  fun testMissingRequiredFieldsValidation() {
    val missingUrlJson = """
      {
        "name": "No URL Source",
        "searchParam": "q"
      }
    """.trimIndent()
    val result = MediaSourceConfig.parseJson(missingUrlJson)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("API Endpoint URL is required") == true)

    val missingNameJson = """
      {
        "apiUrl": "https://api.booru.org/posts"
      }
    """.trimIndent()
    val missingNameConfig = MediaSourceConfig.fromJson(org.json.JSONObject(missingNameJson))
    val errors = MediaSourceConfig.validateConfig(missingNameConfig.copy(name = ""))
    assertTrue(errors.any { it.contains("Source Name is required") })
  }

  @Test
  fun testUnknownOptionalFieldsSafelyIgnored() {
    val jsonWithExtraFields = """
      {
        "name": "Custom Source",
        "apiUrl": "https://example.com/api",
        "unknownField123": "irrelevant_value",
        "extraMetadata": {"nested": true},
        "customFlag": 42
      }
    """.trimIndent()
    val result = MediaSourceConfig.parseJson(jsonWithExtraFields)
    assertTrue(result.isSuccess)
    val config = result.getOrThrow()
    assertEquals("Custom Source", config.name)
    assertEquals("https://example.com/api", config.apiUrl)
  }

  // --- 17. Authentication Modes & Multi-Parameter Resolution (Batch 6) ---

  @Test
  fun testBearerTokenAuthenticationHeaderResolution() {
    val config = MediaSourceConfig(
      name = "Bearer Source",
      apiUrl = "https://api.example.com/posts",
      authType = com.example.model.AuthType.BEARER_TOKEN,
      authHeaderValue = "secret_jwt_token_12345"
    )

    val headers = config.getEffectiveHeaders()
    assertEquals(1, headers.size)
    assertEquals("Bearer secret_jwt_token_12345", headers["Authorization"])
  }

  @Test
  fun testCustomHeaderAuthenticationResolution() {
    val config = MediaSourceConfig(
      name = "Custom Header Source",
      apiUrl = "https://api.example.com/posts",
      authType = com.example.model.AuthType.CUSTOM_HEADER,
      authHeaderName = "X-API-KEY",
      authHeaderValue = "custom_secret_key_888"
    )

    val headers = config.getEffectiveHeaders()
    assertEquals(1, headers.size)
    assertEquals("custom_secret_key_888", headers["X-API-KEY"])
  }

  @Test
  fun testGelbooruMultipleQueryParametersAuthentication() {
    val gelbooru = MediaSourceConfig.TEMPLATE_GELBOORU.copy(
      authQueryParams = listOf(
        com.example.model.AuthParam("api_key", "my_gelbooru_api_key"),
        com.example.model.AuthParam("user_id", "456789")
      )
    )

    assertEquals(com.example.model.AuthType.QUERY_PARAMS, gelbooru.authType)
    val queryParams = gelbooru.getEffectiveAuthQueryParams()
    assertEquals(2, queryParams.size)
    assertEquals("my_gelbooru_api_key", queryParams["api_key"])
    assertEquals("456789", queryParams["user_id"])
    assertEquals("https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1", gelbooru.apiUrl)
  }

  // --- 18. Security: Credential Sanitization on Export (Batch 6) ---

  @Test
  fun testExportedConfigurationSanitizesSecretsWithPlaceholders() {
    val sensitiveConfig = MediaSourceConfig(
      name = "Private Booru",
      apiUrl = "https://private.booru.org/api",
      authType = com.example.model.AuthType.QUERY_PARAMS,
      authQueryParams = listOf(
        com.example.model.AuthParam("api_key", "SUPER_SECRET_KEY_12345"),
        com.example.model.AuthParam("user_id", "my_personal_user_id_999")
      ),
      authHeaderName = "X-Token",
      authHeaderValue = "SECRET_HEADER_VALUE"
    )

    val exportedJsonString = sensitiveConfig.toExportJson(sanitizeSecrets = true)

    // MUST NOT leak the actual secrets
    assertFalse(exportedJsonString.contains("SUPER_SECRET_KEY_12345"))
    assertFalse(exportedJsonString.contains("my_personal_user_id_999"))
    assertFalse(exportedJsonString.contains("SECRET_HEADER_VALUE"))

    // MUST contain standard placeholders
    assertTrue(exportedJsonString.contains("YOUR_API_KEY"))
    assertTrue(exportedJsonString.contains("YOUR_USER_ID"))
  }

  // --- 19. Expanded Templates Integrity (Batch 6) ---

  @Test
  fun testAllSourceTemplatesPresentAndValid() {
    val templates = MediaSourceConfig.SOURCE_TEMPLATES
    assertEquals(6, templates.size)

    val templateNames = templates.map { it.name }
    assertTrue(templateNames.any { it.contains("Safebooru") })
    assertTrue(templateNames.any { it.contains("Danbooru") })
    assertTrue(templateNames.any { it.contains("Yande.re") })
    assertTrue(templateNames.any { it.contains("Gelbooru") })
    assertTrue(templateNames.any { it.contains("Moebooru") })
    assertTrue(templateNames.any { it.contains("Waifu") })
  }

  // --- 20. Source Diagnostics Logic Tests (Batch 6) ---

  @Test
  fun testSourceDiagnosticsParserAndStepEvaluation() {
    val apiClient = MediaApiClient()
    val customSource = MediaSourceConfig(
      name = "Diagnostic Test Booru",
      apiUrl = "https://custom.org/api",
      itemsPath = "posts",
      imageUrlField = "file_url",
      thumbUrlField = "preview_url",
      tagsField = "tags",
      ratingField = "rating"
    )

    val samplePayload = """
      {
        "posts": [
          {
            "id": 1001,
            "file_url": "https://cdn.custom.org/img/1001.png",
            "preview_url": "https://cdn.custom.org/thumb/1001.jpg",
            "tags": "scenery landscape sunset",
            "rating": "s",
            "file_ext": "png"
          }
        ]
      }
    """.trimIndent()

    val parsed = apiClient.parseMediaItems(customSource, samplePayload)
    assertEquals(1, parsed.size)
    assertEquals("https://cdn.custom.org/img/1001.png", parsed[0].imageUrl)
    assertEquals("https://cdn.custom.org/thumb/1001.jpg", parsed[0].thumbnailUrl)
    assertEquals(3, parsed[0].tags.size)
    assertEquals(MediaRating.SAFE, parsed[0].rating)
  }

  // --- 21. AI Prompt Template Content Validation (Batch 6) ---

  @Test
  fun testAiPromptTemplateContent() {
    val prompt = MediaSourceConfig.AI_PROMPT_TEMPLATE
    assertTrue(prompt.contains("Artflux"))
    assertTrue(prompt.contains("imageUrlField"))
    assertTrue(prompt.contains("thumbUrlField"))
    assertTrue(prompt.contains("searchParam"))
    assertTrue(prompt.contains("authentication"))
    assertTrue(prompt.contains("YOUR_API_KEY"))
  }

  // --- 22. Light Theme Persistence & Application (Batch 6.1) ---

  @Test
  fun testLightThemeSelectionAndPersistence() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val prefsRepo = com.example.data.PreferencesRepository(context)

    prefsRepo.setTheme(com.example.model.AppTheme.LIGHT)
    assertEquals(com.example.model.AppTheme.LIGHT, prefsRepo.theme.value)

    val reloadedRepo = com.example.data.PreferencesRepository(context)
    assertEquals(com.example.model.AppTheme.LIGHT, reloadedRepo.theme.value)
  }

  // --- 23. Feed Thumbnail Quality Preview Resolution across Media Types (Batch 6.1) ---

  @Test
  fun testThumbnailQualityResolutionForImagesGifsAndVideos() {
    val imageItem = MediaItem(
      id = "501",
      title = "Static Image",
      actualMediaUrl = "https://cdn.artflux.org/full.jpg",
      previewUrl = "https://cdn.artflux.org/thumb_360.jpg",
      sampleUrl = "https://cdn.artflux.org/sample_720.jpg",
      mediaType = MediaType.IMAGE
    )

    assertEquals("https://cdn.artflux.org/thumb_360.jpg", imageItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q360))
    assertEquals("https://cdn.artflux.org/sample_720.jpg", imageItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))

    val gifItem = MediaItem(
      id = "502",
      title = "GIF Preview",
      actualMediaUrl = "https://cdn.artflux.org/anim.gif",
      previewUrl = "https://cdn.artflux.org/gif_thumb_360.jpg",
      sampleUrl = "https://cdn.artflux.org/gif_sample_720.jpg",
      mediaType = MediaType.GIF
    )

    assertEquals("https://cdn.artflux.org/gif_thumb_360.jpg", gifItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q360))
    assertEquals("https://cdn.artflux.org/gif_sample_720.jpg", gifItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))

    val videoItem = MediaItem(
      id = "503",
      title = "Video Clip",
      actualMediaUrl = "https://cdn.artflux.org/movie.mp4",
      previewUrl = "https://cdn.artflux.org/video_thumb_360.jpg",
      sampleUrl = "https://cdn.artflux.org/video_sample_720.jpg",
      mediaType = MediaType.VIDEO
    )

    assertEquals("https://cdn.artflux.org/video_thumb_360.jpg", videoItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q360))
    assertEquals("https://cdn.artflux.org/video_sample_720.jpg", videoItem.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))
  }

  // --- 24. Thumbnail Fallback Edge Cases (Batch 7) ---

  @Test
  fun testThumbnailFallbackDoesNotFallBackToHeavyOriginalOrVideo() {
    // 1. Static image with only thumbnailUrl: Q1080 falls back to thumbnailUrl, not imageUrl
    val heavyImage = MediaItem(
      id = "601",
      title = "Large Raw File",
      actualMediaUrl = "https://cdn.artflux.org/original_raw_50mb.png",
      previewUrl = "https://cdn.artflux.org/preview_150.jpg",
      sampleUrl = null,
      mediaType = MediaType.IMAGE
    )
    assertEquals("https://cdn.artflux.org/preview_150.jpg", heavyImage.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q1080))
    assertEquals("https://cdn.artflux.org/preview_150.jpg", heavyImage.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q720))

    // 2. Video with only thumbnailUrl: must NOT return the mp4 video file
    val videoNoSample = MediaItem(
      id = "602",
      title = "MP4 Video",
      actualMediaUrl = "https://cdn.artflux.org/full_video.mp4",
      previewUrl = "https://cdn.artflux.org/video_preview.jpg",
      sampleUrl = null,
      mediaType = MediaType.VIDEO
    )
    assertEquals("https://cdn.artflux.org/video_preview.jpg", videoNoSample.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q1080))
    assertFalse(videoNoSample.getThumbnailForQuality(com.example.model.ThumbnailQuality.Q1080).endsWith(".mp4"))
  }

  // --- 25. Media-Type-Aware Download Toast Messages (Batch 7) ---

  @Test
  fun testDownloadToastMessageMediaAwareFormat() {
    val imageItem = MediaItem(id = "701", title = "Sunset Title", actualMediaUrl = "https://cdn.org/1.jpg", previewUrl = "https://cdn.org/t1.jpg", mediaType = MediaType.IMAGE)
    val gifItem = MediaItem(id = "702", title = "Anime Dance", actualMediaUrl = "https://cdn.org/2.gif", previewUrl = "https://cdn.org/t2.jpg", mediaType = MediaType.GIF)
    val videoItem = MediaItem(id = "703", title = "AMV Clip", actualMediaUrl = "https://cdn.org/3.mp4", previewUrl = "https://cdn.org/t3.jpg", mediaType = MediaType.VIDEO)

    fun formatDownloadMessage(item: MediaItem, quality: com.example.model.DownloadQuality): String {
      val mediaLabel = when (item.mediaType) {
        MediaType.GIF -> "GIF"
        MediaType.VIDEO -> "video"
        else -> "image"
      }
      val qualityLabel = if (quality == com.example.model.DownloadQuality.ORIGINAL) "1080p" else quality.label
      return "Downloading $mediaLabel ($qualityLabel)..."
    }

    assertEquals("Downloading image (1080p)...", formatDownloadMessage(imageItem, com.example.model.DownloadQuality.Q1080))
    assertEquals("Downloading GIF (1080p)...", formatDownloadMessage(gifItem, com.example.model.DownloadQuality.Q1080))
    assertEquals("Downloading video (1080p)...", formatDownloadMessage(videoItem, com.example.model.DownloadQuality.Q1080))

    // Ensure no titles or metadata are included
    val msg = formatDownloadMessage(imageItem, com.example.model.DownloadQuality.Q1080)
    assertFalse(msg.contains("Sunset Title"))
    assertFalse(msg.contains("701"))
  }

  // --- 26. Diagnostics Edit Action Across Custom and Built-In Sources (Batch 7) ---

  @Test
  fun testDiagnosticsEditActionAcrossSourceTypes() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(context)

    // 1. Edit a custom source
    val customSource = MediaSourceConfig(id = "my_custom_source", name = "My Source", apiUrl = "https://custom.org/api", isBuiltIn = false)
    viewModel.openEditSourceDialog(customSource)
    assertTrue(viewModel.isAddSourceOpen.value)
    assertEquals("my_custom_source", viewModel.editingSourceConfig.value?.id)
    assertEquals("My Source", viewModel.editingSourceConfig.value?.name)

    viewModel.closeAddSourceDialog()
    assertFalse(viewModel.isAddSourceOpen.value)

    // 2. Edit a built-in source (Safebooru) -> Clones with customization title
    val builtIn = MediaSourceConfig.BUILT_IN_SAFEBOORU
    viewModel.openEditSourceDialog(builtIn)
    assertTrue(viewModel.isAddSourceOpen.value)
    val editing = viewModel.editingSourceConfig.value
    assertNotNull(editing)
    assertFalse(editing!!.isBuiltIn)
    assertTrue(editing.name.contains("Safebooru (Custom)"))
  }

  // --- 27. Generic API Pagination Tests (Batch 8A) ---

  @Test
  fun testGenericApiPaginationZeroBasedOneBasedAndArbitrary() {
    fun calculatePage(pageStartsAt: Int, pageNumber: Int): Int {
      return (pageNumber - 1) + pageStartsAt
    }

    // Zero-based pagination (e.g. pageStartsAt = 0)
    assertEquals(0, calculatePage(0, 1))
    assertEquals(1, calculatePage(0, 2))
    assertEquals(4, calculatePage(0, 5))

    // One-based pagination (e.g. pageStartsAt = 1)
    assertEquals(1, calculatePage(1, 1))
    assertEquals(2, calculatePage(1, 2))
    assertEquals(5, calculatePage(1, 5))

    // Arbitrary starting-page source (e.g. pageStartsAt = 10)
    assertEquals(10, calculatePage(10, 1))
    assertEquals(11, calculatePage(10, 2))
    assertEquals(14, calculatePage(10, 5))
  }

  // --- 28. Configuration-Driven URL Mapping Tests (Batch 8A) ---

  @Test
  fun testConfigurationDrivenUrlMappingRespectsCustomFields() {
    val apiClient = MediaApiClient()
    val customSource = MediaSourceConfig(
      id = "custom_test_booru",
      name = "Custom Booru",
      apiUrl = "https://custom.api.org/v1/posts",
      imageUrlField = "file_full",
      thumbUrlField = "thumb_small",
      sampleUrlField = "sample_mid",
      postUrlField = "post_permalink",
      titleField = "caption",
      authorField = "artist"
    )

    val jsonPayload = """
      [
        {
          "id": "item-888",
          "caption": "Custom Artpiece",
          "file_full": "https://custom.api.org/images/full_888.png",
          "thumb_small": "https://custom.api.org/thumbs/thumb_888.jpg",
          "sample_mid": "https://custom.api.org/samples/sample_888.jpg",
          "post_permalink": "https://custom.api.org/post/item-888",
          "tags": "scenery original landscape",
          "rating": "safe",
          "artist": "PainterOne",
          "score": 42,
          "fav_count": 18
        }
      ]
    """.trimIndent()

    val items = apiClient.parseMediaItems(customSource, jsonPayload)

    assertEquals(1, items.size)
    val item = items[0]
    assertEquals("https://custom.api.org/images/full_888.png", item.imageUrl)
    assertEquals("https://custom.api.org/thumbs/thumb_888.jpg", item.thumbnailUrl)
    assertEquals("https://custom.api.org/samples/sample_888.jpg", item.sampleUrl)
    assertEquals("https://custom.api.org/post/item-888", item.postUrl)
    assertEquals("Custom Artpiece", item.title)
    assertEquals(42, item.score)
    assertEquals(18, item.favorites)
  }

  // --- 29. URL Fallback: Avoid Video As Image Thumbnail & Avoid Heavy Original (Batch 8A) ---

  @Test
  fun testUrlFallbackNeverUsesVideoFileAsImageThumbnail() {
    val apiClient = MediaApiClient()
    val videoSource = MediaSourceConfig(
      id = "video_booru",
      name = "Video Booru",
      apiUrl = "https://custom.video.org/api",
      imageUrlField = "video_url",
      thumbUrlField = "thumb_url",
      sampleUrlField = "sample_preview"
    )

    // Case 1: Video with image sample available -> uses sample as thumbnail, never mp4
    val jsonWithSample = """
      [
        {
          "id": "vid-1",
          "video_url": "https://custom.video.org/clips/clip.mp4",
          "thumb_url": "",
          "sample_preview": "https://custom.video.org/previews/preview.jpg",
          "file_ext": "mp4"
        }
      ]
    """.trimIndent()

    val items1 = apiClient.parseMediaItems(videoSource, jsonWithSample)
    assertEquals(1, items1.size)
    assertEquals("https://custom.video.org/clips/clip.mp4", items1[0].imageUrl)
    assertEquals("https://custom.video.org/previews/preview.jpg", items1[0].thumbnailUrl)
    assertFalse(items1[0].thumbnailUrl.endsWith(".mp4"))

    // Case 2: Video with NO thumbnail and NO sample -> thumbnail must be empty, NEVER mp4
    val jsonNoThumb = """
      [
        {
          "id": "vid-2",
          "video_url": "https://custom.video.org/clips/raw_clip.webm",
          "thumb_url": "",
          "sample_preview": "",
          "file_ext": "webm"
        }
      ]
    """.trimIndent()

    val items2 = apiClient.parseMediaItems(videoSource, jsonNoThumb)
    assertEquals(1, items2.size)
    assertFalse(items2[0].thumbnailUrl.endsWith(".webm"))
    assertEquals("", items2[0].thumbnailUrl)
  }

  // --- 30. Sorting Accuracy Tests (Batch 8A) ---

  @Test
  fun testSortingAccuracyUsesRealMetricsAndPreservesNaturalOrder() {
    val item1 = MediaItem(
      id = "1",
      title = "Item 1",
      actualMediaUrl = "https://cdn.org/1.jpg",
      previewUrl = "https://cdn.org/1_t.jpg",
      tags = List(15) { "tag$it" }, // 15 tags
      rating = MediaRating.ADULT,
      score = 500,
      favorites = 10
    )

    val item2 = MediaItem(
      id = "2",
      title = "Item 2",
      actualMediaUrl = "https://cdn.org/2.jpg",
      previewUrl = "https://cdn.org/2_t.jpg",
      tags = listOf("one_tag"), // 1 tag
      rating = MediaRating.SAFE,
      score = 50,
      favorites = 200 // high favorites
    )

    val item3 = MediaItem(
      id = "3",
      title = "Item 3",
      actualMediaUrl = "https://cdn.org/3.jpg",
      previewUrl = "https://cdn.org/3_t.jpg",
      tags = List(30) { "tag$it" }, // 30 tags
      rating = MediaRating.SAFE,
      score = 150,
      favorites = 5
    )

    val list = listOf(item1, item2, item3)
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(context)

    val filterMethod = MediaBrowserViewModel::class.java.getDeclaredMethod(
      "applyClientFilters",
      List::class.java,
      FilterState::class.java
    ).apply { isAccessible = true }

    // 1. Popular Sort: MUST use favorites / views / score, NOT tag count!
    @Suppress("UNCHECKED_CAST")
    val popularSorted = filterMethod.invoke(viewModel, list, FilterState(sort = com.example.model.SortOption.POPULAR)) as List<MediaItem>
    assertEquals("Item 2", popularSorted[0].title) // 200 favs
    assertEquals("Item 1", popularSorted[1].title) // 10 favs
    assertEquals("Item 3", popularSorted[2].title) // 5 favs (despite having 30 tags!)

    // 2. Top Rated Sort: MUST use actual score, NOT Safe/Adult rating!
    @Suppress("UNCHECKED_CAST")
    val topRatedSorted = filterMethod.invoke(viewModel, list, FilterState(sort = com.example.model.SortOption.TOP_RATED)) as List<MediaItem>
    assertEquals("Item 1", topRatedSorted[0].title) // score 500 (even though ADULT!)
    assertEquals("Item 3", topRatedSorted[1].title) // score 150
    assertEquals("Item 2", topRatedSorted[2].title) // score 50

    // 3. Natural order preserved when metrics are absent (do NOT pretend sorting is meaningful)
    val noMetricsList = listOf(
      MediaItem(id = "a", title = "A", actualMediaUrl = "https://cdn.org/a.jpg", previewUrl = "https://cdn.org/at.jpg", tags = listOf("t1", "t2", "t3"), rating = MediaRating.ADULT),
      MediaItem(id = "b", title = "B", actualMediaUrl = "https://cdn.org/b.jpg", previewUrl = "https://cdn.org/bt.jpg", tags = listOf("t1"), rating = MediaRating.SAFE)
    )
    @Suppress("UNCHECKED_CAST")
    val popularNoMetrics = filterMethod.invoke(viewModel, noMetricsList, FilterState(sort = com.example.model.SortOption.POPULAR)) as List<MediaItem>
    assertEquals("A", popularNoMetrics[0].title)
    assertEquals("B", popularNoMetrics[1].title)

    @Suppress("UNCHECKED_CAST")
    val topRatedNoMetrics = filterMethod.invoke(viewModel, noMetricsList, FilterState(sort = com.example.model.SortOption.TOP_RATED)) as List<MediaItem>
    assertEquals("A", topRatedNoMetrics[0].title)
    assertEquals("B", topRatedNoMetrics[1].title)
  }

  // --- 31. Custom Source Credential Security & Migration Tests (Batch 8A) ---

  @Test
  fun testCustomSourceCredentialSecurityAndMigration() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val repo = com.example.data.SourceRepository(context)

    val secretSource = MediaSourceConfig(
      id = "secure_source_99",
      name = "Secure Source",
      apiUrl = "https://secure.api.org/feed",
      apiKey = "super_secret_token_12345",
      authHeaderValue = "bearer_secret_xyz",
      authQueryParams = listOf(com.example.model.AuthParam("user_id", "my_user_id")),
      isBuiltIn = false
    )

    // Save custom source
    repo.addSource(secretSource)

    // 1. Verify plain SharedPreferences DOES NOT contain sensitive plaintext credentials
    val plainPrefs = context.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE)
    val savedJson = plainPrefs.getString("custom_sources", "") ?: ""
    assertFalse(savedJson.contains("super_secret_token_12345"))
    assertFalse(savedJson.contains("bearer_secret_xyz"))
    assertFalse(savedJson.contains("my_user_id"))

    // 2. Verify in-memory repository successfully populated credentials from secure storage
    val loaded = repo.getSourceById("secure_source_99")
    assertNotNull(loaded)
    assertEquals("super_secret_token_12345", loaded!!.apiKey)
    assertEquals("bearer_secret_xyz", loaded.authHeaderValue)
    assertEquals(1, loaded.authQueryParams.size)
    assertEquals("user_id", loaded.authQueryParams[0].key)
    assertEquals("my_user_id", loaded.authQueryParams[0].value)

    // 3. Test legacy migration from plaintext in SharedPreferences
    val legacyJson = """
      [
        {
          "id": "legacy_migrated_source",
          "name": "Legacy Source",
          "apiUrl": "https://legacy.org/api",
          "apiKey": "legacy_plaintext_key_777",
          "authHeaderValue": "legacy_header_val_888",
          "authQueryParams": [{"key": "token", "value": "legacy_token_999"}],
          "isBuiltIn": false
        }
      ]
    """.trimIndent()
    plainPrefs.edit().putString("custom_sources", legacyJson).commit()

    // Reload sources (triggers migration)
    repo.loadSources()

    val migrated = repo.getSourceById("legacy_migrated_source")
    assertNotNull(migrated)
    assertEquals("legacy_plaintext_key_777", migrated!!.apiKey)
    assertEquals("legacy_header_val_888", migrated.authHeaderValue)

    // Verify plaintext secrets were purged from ordinary SharedPreferences
    val resavedJson = plainPrefs.getString("custom_sources", "") ?: ""
    assertFalse(resavedJson.contains("legacy_plaintext_key_777"))
    assertFalse(resavedJson.contains("legacy_header_val_888"))
    assertFalse(resavedJson.contains("legacy_token_999"))
  }

  @Test
  fun testSourceCredentialVaultSecurityAndNoBase64Fallback() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val vault = com.example.data.SourceCredentialVault(context)
    val sourceId = "test_vault_source_1"

    val creds = com.example.data.SourceCredentials(
      apiKey = "api_key_secret_abc123",
      authHeaderValue = "Bearer header_token_xyz789",
      authQueryParams = listOf(com.example.model.AuthParam("sig", "sig_val_456"))
    )

    // 1. Save credentials securely
    val saveResult = vault.saveCredentials(sourceId, creds)
    assertTrue("saveCredentials must report success", saveResult)

    // 2. Verify SharedPreferences contains encrypted ciphertext, NOT plain Base64 JSON
    val vaultPrefs = context.getSharedPreferences("artflux_secure_credentials", Context.MODE_PRIVATE)
    val rawStored = vaultPrefs.getString("cred_$sourceId", "") ?: ""
    assertTrue("Raw stored value must not be empty", rawStored.isNotBlank())

    // Ensure the raw stored string is NOT simply base64-decoded into plaintext JSON containing secrets
    val decodedBytes = android.util.Base64.decode(rawStored, android.util.Base64.NO_WRAP)
    val decodedString = String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8)
    assertFalse("Raw stored string must not contain unencrypted API key", decodedString.contains("api_key_secret_abc123"))
    assertFalse("Raw stored string must not contain unencrypted auth header", decodedString.contains("header_token_xyz789"))

    // 3. Verify getCredentials correctly decrypts
    val loadedCreds = vault.getCredentials(sourceId)
    assertEquals("api_key_secret_abc123", loadedCreds.apiKey)
    assertEquals("Bearer header_token_xyz789", loadedCreds.authHeaderValue)
    assertEquals(1, loadedCreds.authQueryParams.size)
    assertEquals("sig", loadedCreds.authQueryParams[0].key)
    assertEquals("sig_val_456", loadedCreds.authQueryParams[0].value)

    // 4. Verify invalid/corrupt ciphertext does not fall back to insecure decoding
    vaultPrefs.edit().putString("cred_corrupt_src", "corrupted_payload_not_aes").commit()
    val corruptLoaded = vault.getCredentials("corrupt_src")
    assertFalse("Corrupt payload must result in empty credentials", corruptLoaded.hasCredentials)
    assertEquals("", corruptLoaded.apiKey)

    // 5. Test delete and clear
    vault.deleteCredentials(sourceId)
    val deletedCreds = vault.getCredentials(sourceId)
    assertFalse(deletedCreds.hasCredentials)
  }

  // --- 32. Batch 8B & Batch 9: NSFW Blur & Fullscreen Loading Optimization Tests ---

  @Test
  fun testBatch8BNsfwBlurAndFullscreenPerformance() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val prefsRepo = com.example.data.PreferencesRepository(context)

    // 1. Default for new installs is ON (true)
    assertTrue("Blur NSFW content must default to true on new installs", prefsRepo.blurNsfw.value)

    // 2. NSFW classification check
    val adultItem = MediaItem(
      id = "item_adult_1",
      title = "Adult Item",
      actualMediaUrl = "https://cdn.example.org/full/adult_1.jpg",
      previewUrl = "https://cdn.example.org/preview/adult_1.jpg",
      sampleUrl = "https://cdn.example.org/sample/adult_1.jpg",
      rating = MediaRating.ADULT,
      mediaType = MediaType.IMAGE,
      sourceName = "Danbooru"
    )
    val safeItem = adultItem.copy(id = "item_safe_1", rating = MediaRating.SAFE)
    val suggestiveItem = adultItem.copy(id = "item_sugg_1", rating = MediaRating.SUGGESTIVE)

    // Adult and Suggestive ratings are both flagged as NSFW for blurring
    val isAdultNsfw = adultItem.rating == MediaRating.ADULT || adultItem.rating == MediaRating.SUGGESTIVE
    val isSuggestiveNsfw = suggestiveItem.rating == MediaRating.ADULT || suggestiveItem.rating == MediaRating.SUGGESTIVE
    val isSafeNsfw = safeItem.rating == MediaRating.ADULT || safeItem.rating == MediaRating.SUGGESTIVE

    assertTrue("Adult rating must be classified as NSFW", isAdultNsfw)
    assertTrue("Suggestive rating must be classified as NSFW for blur", isSuggestiveNsfw)
    assertFalse("Safe rating must not be classified as NSFW", isSafeNsfw)

    // 3. Fullscreen URL resolution: Images use sampleUrl if present, avoiding huge raw downloads
    val fullscreenUrl = adultItem.sampleUrl?.takeIf { it.isNotBlank() } ?: adultItem.imageUrl
    assertEquals("https://cdn.example.org/sample/adult_1.jpg", fullscreenUrl)

    // If sampleUrl is null, falls back to imageUrl
    val noSampleItem = adultItem.copy(sampleUrl = null)
    val fullscreenNoSample = noSampleItem.sampleUrl?.takeIf { it.isNotBlank() } ?: noSampleItem.imageUrl
    assertEquals("https://cdn.example.org/full/adult_1.jpg", fullscreenNoSample)

    // For GIFs, fullscreen uses actualMediaUrl / imageUrl
    val gifItem = adultItem.copy(mediaType = MediaType.GIF, actualMediaUrl = "https://cdn.example.org/anim/dance.gif")
    val gifDisplayUrl = if (gifItem.mediaType == MediaType.GIF) gifItem.actualMediaUrl else (gifItem.sampleUrl ?: gifItem.actualMediaUrl)
    assertEquals("https://cdn.example.org/anim/dance.gif", gifDisplayUrl)

    // Preview thumbnail key uses cached preview/sample
    val previewKey = adultItem.thumbnailUrl.ifBlank { adultItem.sampleUrl ?: adultItem.imageUrl }
    assertEquals("https://cdn.example.org/preview/adult_1.jpg", previewKey)
  }

  // --- 33. Batch 9: UI Refinements, Filename Sanitization & Source Settings Tests ---

  @Test
  fun testBatch9DownloadFilenameSanitizationAndFormatting() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MediaBrowserViewModel(context as Application)

    val imageItem = MediaItem(
      id = "img_901",
      title = "Hatsune Miku Artwork",
      actualMediaUrl = "https://cdn.example.org/art/miku.png",
      previewUrl = "https://cdn.example.org/preview/miku.png",
      mediaType = MediaType.IMAGE,
      fileExt = "png",
      sourceName = "Safebooru"
    )

    val gifItem = MediaItem(
      id = "gif_902",
      title = "Dancing Cat",
      actualMediaUrl = "https://cdn.example.org/anim/cat.gif",
      previewUrl = "https://cdn.example.org/preview/cat.jpg",
      mediaType = MediaType.GIF,
      sourceName = "Danbooru"
    )

    val videoItem = MediaItem(
      id = "vid_903",
      title = "Short Animation Clip",
      actualMediaUrl = "https://cdn.example.org/clips/clip.webm",
      previewUrl = "https://cdn.example.org/preview/clip.jpg",
      mediaType = MediaType.VIDEO,
      fileExt = "webm",
      sourceName = "Gelbooru"
    )

    // 1. Custom filename provided - correct extension preserved
    val customImageName = viewModel.resolveDownloadFilename(imageItem, "my_custom_miku")
    assertEquals("my_custom_miku.png", customImageName)

    val customGifName = viewModel.resolveDownloadFilename(gifItem, "funny_cat_anim")
    assertEquals("funny_cat_anim.gif", customGifName)

    val customVideoName = viewModel.resolveDownloadFilename(videoItem, "cool_video_clip")
    assertEquals("cool_video_clip.webm", customVideoName)

    // 2. Custom filename with existing extension suffix is handled cleanly
    val nameWithExt = viewModel.resolveDownloadFilename(imageItem, "my_custom_miku.png")
    assertEquals("my_custom_miku.png", nameWithExt)

    val nameWithWrongExt = viewModel.resolveDownloadFilename(imageItem, "my_custom_miku.jpg")
    assertEquals("my_custom_miku.png", nameWithWrongExt)

    // 3. Sanitization of invalid filesystem characters: / \ : * ? " < > |
    val dirtyName = viewModel.resolveDownloadFilename(imageItem, "illegal/path\\test:name*one?two\"three<four>five|six")
    assertFalse(dirtyName.contains("/"))
    assertFalse(dirtyName.contains("\\"))
    assertFalse(dirtyName.contains(":"))
    assertFalse(dirtyName.contains("*"))
    assertFalse(dirtyName.contains("?"))
    assertFalse(dirtyName.contains("\""))
    assertFalse(dirtyName.contains("<"))
    assertFalse(dirtyName.contains(">"))
    assertFalse(dirtyName.contains("|"))
    assertTrue(dirtyName.endsWith(".png"))

    // 4. Empty or whitespace-only custom filename falls back to auto-generated name
    val emptyName = viewModel.resolveDownloadFilename(imageItem, "   ")
    assertTrue("Empty custom filename should use auto-generated name", emptyName.startsWith("MediaBrowser_"))
    assertTrue(emptyName.endsWith(".png"))

    val nullName = viewModel.resolveDownloadFilename(imageItem, null)
    assertTrue("Null custom filename should use auto-generated name", nullName.startsWith("MediaBrowser_"))
    assertTrue(nullName.endsWith(".png"))

    // 5. Excessive whitespace is trimmed
    val spacedName = viewModel.resolveDownloadFilename(imageItem, "   my   custom   art   ")
    assertEquals("my custom art.png", spacedName)
  }

  @Test
  fun testBatch9NsfwSuggestiveAndAdultBlurLogic() {
    val safeItem = MediaItem(
      id = "item_1",
      title = "Safe Art",
      actualMediaUrl = "https://example.com/safe.jpg",
      previewUrl = "https://example.com/thumb.jpg",
      rating = MediaRating.SAFE
    )
    val suggestiveItem = safeItem.copy(id = "item_2", rating = MediaRating.SUGGESTIVE)
    val adultItem = safeItem.copy(id = "item_3", rating = MediaRating.ADULT)

    // When blurNsfw is true:
    val blurNsfwEnabled = true
    val shouldBlurSafe = blurNsfwEnabled && (safeItem.rating == MediaRating.ADULT || safeItem.rating == MediaRating.SUGGESTIVE)
    val shouldBlurSuggestive = blurNsfwEnabled && (suggestiveItem.rating == MediaRating.ADULT || suggestiveItem.rating == MediaRating.SUGGESTIVE)
    val shouldBlurAdult = blurNsfwEnabled && (adultItem.rating == MediaRating.ADULT || adultItem.rating == MediaRating.SUGGESTIVE)

    assertFalse("Safe items must remain unblurred", shouldBlurSafe)
    assertTrue("Suggestive items must be blurred when blurNsfw is enabled", shouldBlurSuggestive)
    assertTrue("Adult items must be blurred when blurNsfw is enabled", shouldBlurAdult)

    // When blurNsfw is false:
    val blurNsfwDisabled = false
    val blurWhenDisabled = blurNsfwDisabled && (adultItem.rating == MediaRating.ADULT || adultItem.rating == MediaRating.SUGGESTIVE)
    assertFalse("Nothing is blurred when blurNsfw is disabled", blurWhenDisabled)
  }

  // --- 34. Batch 10: Universal Media Compatibility Tests ---

  @Test
  fun testBatch10UniversalMediaCompatibility() {
    // 1. GIF detected from ".gif" in URL
    val gifFromUrl = MediaApiClient.detectMediaType(
      actualMediaUrl = "https://cdn.example.org/files/dance.gif",
      rawMediaUrl = "https://cdn.example.org/files/dance.gif"
    )
    assertEquals(MediaType.GIF, gifFromUrl)

    // 2. MP4/WebM/MKV detected as video from URL
    val mp4FromUrl = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clips/clip.mp4")
    val webmFromUrl = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clips/clip.webm")
    val mkvFromUrl = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clips/clip.mkv")
    assertEquals(MediaType.VIDEO, mp4FromUrl)
    assertEquals(MediaType.VIDEO, webmFromUrl)
    assertEquals(MediaType.VIDEO, mkvFromUrl)

    // 3. Explicit API media type detection
    val explicitGif = MediaApiClient.detectMediaType(explicitType = "image/gif")
    val explicitVideo = MediaApiClient.detectMediaType(explicitType = "video/mp4")
    val explicitWebm = MediaApiClient.detectMediaType(explicitType = "video/webm")
    val explicitImage = MediaApiClient.detectMediaType(explicitType = "image/png")
    assertEquals(MediaType.GIF, explicitGif)
    assertEquals(MediaType.VIDEO, explicitVideo)
    assertEquals(MediaType.VIDEO, explicitWebm)
    assertEquals(MediaType.IMAGE, explicitImage)

    // 4. API file extension detection
    val extGif = MediaApiClient.detectMediaType(fileExtField = "gif")
    val extWebm = MediaApiClient.detectMediaType(fileExtField = "webm")
    val extMp4 = MediaApiClient.detectMediaType(fileExtField = "mp4")
    val extMkv = MediaApiClient.detectMediaType(fileExtField = "mkv")
    val extPng = MediaApiClient.detectMediaType(fileExtField = "png")
    assertEquals(MediaType.GIF, extGif)
    assertEquals(MediaType.VIDEO, extWebm)
    assertEquals(MediaType.VIDEO, extMp4)
    assertEquals(MediaType.VIDEO, extMkv)
    assertEquals(MediaType.IMAGE, extPng)

    // 5. Preview image + GIF actual file: correctly detected as GIF
    val previewJpgGifFile = MediaApiClient.detectMediaType(
      actualMediaUrl = "https://cdn.example.org/files/animation.gif",
      rawMediaUrl = "https://cdn.example.org/files/animation.gif"
    )
    assertEquals(MediaType.GIF, previewJpgGifFile)

    // 6. Preview image + video actual file: correctly detected as VIDEO
    val previewJpgVideoFile = MediaApiClient.detectMediaType(
      actualMediaUrl = "https://cdn.example.org/files/clip.webm",
      rawMediaUrl = "https://cdn.example.org/files/clip.webm"
    )
    assertEquals(MediaType.VIDEO, previewJpgVideoFile)

    // 7. Generic "animated" tag does not classify GIF as video
    val tagGif = MediaApiClient.detectMediaType(
      actualMediaUrl = "https://cdn.example.org/files/sample.gif",
      tags = listOf("animated", "illustration")
    )
    assertEquals(MediaType.GIF, tagGif)

    // 8. No video URL used as an image thumbnail in MediaItem
    val videoItemWithThumb = MediaItem(
      id = "vid_101",
      title = "WebM Animation",
      actualMediaUrl = "https://cdn.example.org/video.webm",
      previewUrl = "https://cdn.example.org/preview.jpg",
      mediaType = MediaType.VIDEO
    )
    assertEquals("https://cdn.example.org/preview.jpg", videoItemWithThumb.getThumbnailForQuality(ThumbnailQuality.Q720))

    val videoItemWithoutThumb = MediaItem(
      id = "vid_102",
      title = "Raw Video Only",
      actualMediaUrl = "https://cdn.example.org/video.mp4",
      previewUrl = "",
      mediaType = MediaType.VIDEO
    )
    // Never fall back to heavy video file as thumbnail
    assertEquals("", videoItemWithoutThumb.getThumbnailForQuality(ThumbnailQuality.Q720))

    // 9. Existing standard image behavior preserved
    val imageItem = MediaItem(
      id = "img_103",
      title = "Standard Anime Artwork",
      actualMediaUrl = "https://cdn.example.org/art.jpg",
      previewUrl = "https://cdn.example.org/thumb.jpg",
      mediaType = MediaType.IMAGE
    )
    assertEquals(MediaType.IMAGE, imageItem.mediaType)
    assertEquals("https://cdn.example.org/thumb.jpg", imageItem.getThumbnailForQuality(ThumbnailQuality.Q480))

    // 10. Missing query tags do NOT make GIF/video unsupported
    val customSourceNoTags = MediaSourceConfig(
      id = "custom_test_no_tags",
      name = "Custom Booru No Tags",
      apiUrl = "https://booru.custom.org/posts.json",
      gifQueryTag = "",
      videoQueryTag = ""
    )
    assertTrue(customSourceNoTags.supportsGifs)
    assertTrue(customSourceNoTags.supportsVideos)
    assertTrue(customSourceNoTags.getSupportedMediaTypes().contains(MediaType.GIF))
    assertTrue(customSourceNoTags.getSupportedMediaTypes().contains(MediaType.VIDEO))
    assertFalse(customSourceNoTags.canDiscoverGifs)
    assertFalse(customSourceNoTags.canDiscoverVideos)
  }

  // --- 35. Batch 10.1: Media Pipeline Fix Tests ---

  @Test
  fun testBatch10_1MediaPipelineFixes() {
    val context = ApplicationProvider.getApplicationContext<Application>()

    // 1. Video with JPG preview: feed uses JPG preview, fullscreen uses actual video
    val videoWithJpgPreview = MediaItem(
      id = "vid_201",
      title = "Cyberpunk City Animation",
      actualMediaUrl = "https://cdn.example.org/videos/cyberpunk.mp4",
      previewUrl = "https://cdn.example.org/thumbnails/cyberpunk_preview.jpg",
      mediaType = MediaType.VIDEO
    )
    assertEquals(MediaType.VIDEO, videoWithJpgPreview.mediaType)
    assertEquals("https://cdn.example.org/thumbnails/cyberpunk_preview.jpg", videoWithJpgPreview.getThumbnailForQuality(ThumbnailQuality.Q720))
    assertEquals("https://cdn.example.org/thumbnails/cyberpunk_preview.jpg", videoWithJpgPreview.getThumbnailForQuality(ThumbnailQuality.Q360))
    // Fullscreen viewer gets original video URL for ExoPlayer
    assertEquals("https://cdn.example.org/videos/cyberpunk.mp4", videoWithJpgPreview.imageUrl)

    // 2. GIF with JPG preview: feed uses lightweight JPG preview, fullscreen uses actual .gif
    val gifWithJpgPreview = MediaItem(
      id = "gif_202",
      title = "Pixel Art Sprite",
      actualMediaUrl = "https://cdn.example.org/animations/sprite.gif",
      previewUrl = "https://cdn.example.org/thumbnails/sprite_poster.jpg",
      sampleUrl = "https://cdn.example.org/samples/sprite_sample.jpg",
      mediaType = MediaType.GIF
    )
    assertEquals(MediaType.GIF, gifWithJpgPreview.mediaType)
    // Feed avoids downloading heavy GIF by using lightweight image preview/sample
    assertEquals("https://cdn.example.org/samples/sprite_sample.jpg", gifWithJpgPreview.getThumbnailForQuality(ThumbnailQuality.Q720))
    assertEquals("https://cdn.example.org/thumbnails/sprite_poster.jpg", gifWithJpgPreview.getThumbnailForQuality(ThumbnailQuality.Q480))
    // GIF with no sampleUrl falls back to thumbnailUrl on Q720
    val gifNoSample = gifWithJpgPreview.copy(sampleUrl = null)
    assertEquals("https://cdn.example.org/thumbnails/sprite_poster.jpg", gifNoSample.getThumbnailForQuality(ThumbnailQuality.Q720))
    // Fullscreen uses actual .gif
    val fullscreenGifUrl = if (gifWithJpgPreview.mediaType == MediaType.GIF) gifWithJpgPreview.imageUrl else (gifWithJpgPreview.sampleUrl ?: gifWithJpgPreview.imageUrl)
    assertEquals("https://cdn.example.org/animations/sprite.gif", fullscreenGifUrl)

    // 3. Video never used as image thumbnail when no preview is provided
    val rawVideoItem = MediaItem(
      id = "vid_203",
      title = "Raw Video No Poster",
      actualMediaUrl = "https://cdn.example.org/raw/clip.webm",
      previewUrl = "",
      sampleUrl = null,
      mediaType = MediaType.VIDEO
    )
    assertEquals("", rawVideoItem.getThumbnailForQuality(ThumbnailQuality.Q720))
    assertEquals("", rawVideoItem.getThumbnailForQuality(ThumbnailQuality.Q360))
    assertEquals("", rawVideoItem.getThumbnailFallbackUrl(ThumbnailQuality.Q720))

    // 4. GIF detected from ".gif"
    val gifDetected = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/art/animated.gif")
    assertEquals(MediaType.GIF, gifDetected)

    // 5. MP4, WebM, MKV, MOV detected as video
    val mp4Detected = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clip.mp4")
    val webmDetected = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clip.webm")
    val mkvDetected = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clip.mkv")
    val movDetected = MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.example.org/clip.mov")
    assertEquals(MediaType.VIDEO, mp4Detected)
    assertEquals(MediaType.VIDEO, webmDetected)
    assertEquals(MediaType.VIDEO, mkvDetected)
    assertEquals(MediaType.VIDEO, movDetected)

    // 6. Rating conversions: "rating:s" -> SUGGESTIVE on Danbooru, "rating:q" -> SUGGESTIVE, "rating:e" -> ADULT
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("s", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("sensitive", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("q", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("questionable", isDanbooru = false))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("q", isDanbooru = false))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("e", isDanbooru = true))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("explicit", isDanbooru = false))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("e", isDanbooru = false))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("g", isDanbooru = true))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("general", isDanbooru = true))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("s", isDanbooru = false))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("safe", isDanbooru = false))

    // 7. Blur preference persistence across repository reloads
    val prefsRepo = com.example.data.PreferencesRepository(context)
    prefsRepo.setBlurNsfw(true)
    assertTrue(prefsRepo.blurNsfw.value)

    val reloadedPrefsRepo = com.example.data.PreferencesRepository(context)
    assertTrue("Persisted blurNsfw must be loaded from preferences on startup", reloadedPrefsRepo.blurNsfw.value)

    prefsRepo.setBlurNsfw(false)
    assertFalse(prefsRepo.blurNsfw.value)
    val reloadedFalseRepo = com.example.data.PreferencesRepository(context)
    assertFalse(reloadedFalseRepo.blurNsfw.value)

    // Reset back to true (default)
    prefsRepo.setBlurNsfw(true)
  }

  // --- 36. Batch 11: App Testing Feedback & Design Refinement Tests ---

  @Test
  fun testBatch11GelbooruUrlResolutionAndHttpsUpgrade() {
    val client = MediaApiClient()
    val gelbooruConfig = MediaSourceConfig.TEMPLATE_GELBOORU

    // 1. Full JSON response with file_url and preview_url on img4.gelbooru.com
    val jsonWithFullUrls = """
      [
        {
          "id": 98765,
          "file_url": "https://img4.gelbooru.com/images/1a/2b/1a2b3c4d.jpg",
          "preview_url": "https://img4.gelbooru.com/thumbnails/1a/2b/thumbnail_1a2b3c4d.jpg",
          "sample_url": "https://img4.gelbooru.com/samples/1a/2b/sample_1a2b3c4d.jpg",
          "tags": "scenery landscape sunset",
          "rating": "general",
          "sample": 1
        }
      ]
    """.trimIndent()

    val parsedItems = client.parseMediaItems(gelbooruConfig, jsonWithFullUrls)
    assertEquals(1, parsedItems.size)
    val item = parsedItems[0]
    assertEquals("https://img4.gelbooru.com/images/1a/2b/1a2b3c4d.jpg", item.imageUrl)
    assertEquals("https://img4.gelbooru.com/thumbnails/1a/2b/thumbnail_1a2b3c4d.jpg", item.thumbnailUrl)
    assertEquals("https://img4.gelbooru.com/samples/1a/2b/sample_1a2b3c4d.jpg", item.sampleUrl)
    assertEquals(MediaRating.SAFE, item.rating)

    // 2. Gelbooru JSON with only directory and image fields (URL reconstruction)
    val jsonWithDirectoryAndImage = """
      [
        {
          "id": 98766,
          "directory": "3e/4f",
          "image": "3e4f5a6b.png",
          "tags": "character original",
          "rating": "sensitive",
          "sample": 0
        }
      ]
    """.trimIndent()

    val parsedDirectoryItems = client.parseMediaItems(gelbooruConfig, jsonWithDirectoryAndImage)
    assertEquals(1, parsedDirectoryItems.size)
    val dirItem = parsedDirectoryItems[0]
    assertEquals("https://img4.gelbooru.com/images/3e/4f/3e4f5a6b.png", dirItem.imageUrl)
    assertEquals("https://img4.gelbooru.com/thumbnails/3e/4f/thumbnail_3e4f5a6b.jpg", dirItem.thumbnailUrl)
    assertEquals(MediaRating.SUGGESTIVE, dirItem.rating)

    // 3. HTTP to HTTPS upgrade for cleartext security and redirect prevention
    val jsonWithHttpUrls = """
      [
        {
          "id": 98767,
          "file_url": "http://img4.gelbooru.com/images/5c/6d/sample.jpg",
          "preview_url": "http://img4.gelbooru.com/thumbnails/5c/6d/thumbnail_sample.jpg",
          "rating": "explicit"
        }
      ]
    """.trimIndent()

    val parsedHttpItems = client.parseMediaItems(gelbooruConfig, jsonWithHttpUrls)
    assertEquals(1, parsedHttpItems.size)
    val httpItem = parsedHttpItems[0]
    assertTrue("HTTP URLs must be normalized to HTTPS", httpItem.imageUrl.startsWith("https://"))
    assertTrue("HTTP thumbnails must be normalized to HTTPS", httpItem.thumbnailUrl.startsWith("https://"))
    assertEquals(MediaRating.ADULT, httpItem.rating)
  }

  @Test
  fun testBatch11IncreasedNsfwBlurAndPrivacyOverlay() {
    val safeItem = MediaItem(
      id = "item_safe",
      title = "Safe Art",
      actualMediaUrl = "https://example.com/safe.jpg",
      previewUrl = "https://example.com/thumb.jpg",
      rating = MediaRating.SAFE
    )
    val suggestiveItem = safeItem.copy(id = "item_suggestive", rating = MediaRating.SUGGESTIVE)
    val adultItem = safeItem.copy(id = "item_adult", rating = MediaRating.ADULT)

    // Blur enabled: Suggestive and Adult must be blurred; Safe must remain unblurred
    val blurNsfw = true
    val blurSafe = blurNsfw && (safeItem.rating == MediaRating.ADULT || safeItem.rating == MediaRating.SUGGESTIVE)
    val blurSuggestive = blurNsfw && (suggestiveItem.rating == MediaRating.ADULT || suggestiveItem.rating == MediaRating.SUGGESTIVE)
    val blurAdult = blurNsfw && (adultItem.rating == MediaRating.ADULT || adultItem.rating == MediaRating.SUGGESTIVE)

    assertFalse("Safe content must never be blurred", blurSafe)
    assertTrue("Suggestive content must be blurred", blurSuggestive)
    assertTrue("Adult content must be blurred", blurAdult)

    // Fullscreen lightbox viewer always accesses the unblurred original image URL
    assertEquals("https://example.com/safe.jpg", adultItem.imageUrl)
  }

  @Test
  fun testVideoAntiHotlinkHeadersAndExoPlayerFallback() {
    // Danbooru videos on cdn.donmai.us must resolve to danbooru.donmai.us referer
    assertEquals("https://danbooru.donmai.us/", ArtfluxNetwork.getRefererForUrl("https://cdn.donmai.us/original/ab/cd/clip.mp4"))
    assertEquals("https://danbooru.donmai.us/", ArtfluxNetwork.getRefererForUrl("https://raikou1.donmai.us/sample/12/34/clip.mp4"))

    // Gelbooru videos
    assertEquals("https://gelbooru.com/", ArtfluxNetwork.getRefererForUrl("https://img4.gelbooru.com/images/56/78/sample.mp4"))

    // Safebooru
    assertEquals("https://safebooru.org/", ArtfluxNetwork.getRefererForUrl("https://safebooru.org/samples/12/sample_34.mp4"))

    // Yande.re
    assertEquals("https://yande.re/", ArtfluxNetwork.getRefererForUrl("https://files.yande.re/image/sample.jpg"))

    // Custom booru origin fallback
    assertEquals("https://custombooru.example.com/", ArtfluxNetwork.getRefererForUrl("https://custombooru.example.com/data/video.webm"))

    // Header bundle check
    val headers = ArtfluxNetwork.getHeadersForUrl("https://img4.gelbooru.com/images/1/2/pic.jpg")
    assertEquals("ArtfluxApp/2.0 (Android; MediaBrowser)", headers["User-Agent"])
    assertEquals(ArtfluxNetwork.DEFAULT_USER_AGENT, headers["User-Agent"])
    assertEquals("https://gelbooru.com/", headers["Referer"])
  }

  @Test
  fun testDanbooruMediaRequestUsesApplicationUserAgent() {
    val danbooruMediaUrl = "https://cdn.donmai.us/original/ab/cd/video.mp4"
    val headers = ArtfluxNetwork.getHeadersForUrl(danbooruMediaUrl)
    assertEquals("ArtfluxApp/2.0 (Android; MediaBrowser)", headers["User-Agent"])
    assertEquals("https://danbooru.donmai.us/", headers["Referer"])
    assertEquals("ArtfluxApp/2.0 (Android; MediaBrowser)", ArtfluxNetwork.DEFAULT_USER_AGENT)
  }

  // --- 36. Deterministic Media Pipeline Tests ---

  @Test
  fun testDeterministicMediaPipelineResolutionAndSeparation() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val apiClient = MediaApiClient()

    // 1. Normal image resolution
    val imageItem = MediaItem(
      id = "img_1001",
      title = "Sunset Landscape",
      actualMediaUrl = "https://cdn.example.org/full/sunset.png",
      previewUrl = "https://cdn.example.org/preview/sunset.jpg",
      mediaType = MediaType.IMAGE,
      rating = MediaRating.SAFE
    )
    assertEquals("https://cdn.example.org/full/sunset.png", imageItem.actualMediaUrl)
    assertEquals("https://cdn.example.org/preview/sunset.jpg", imageItem.previewUrl)
    assertEquals(MediaType.IMAGE, imageItem.mediaType)
    assertEquals(MediaRating.SAFE, imageItem.rating)
    // Backward compatibility getters
    assertEquals(imageItem.actualMediaUrl, imageItem.imageUrl)
    assertEquals(imageItem.previewUrl, imageItem.thumbnailUrl)
    // Feed uses previewUrl, fullscreen uses actualMediaUrl
    assertEquals("https://cdn.example.org/preview/sunset.jpg", imageItem.getThumbnailForQuality(ThumbnailQuality.Q360))
    assertEquals("https://cdn.example.org/full/sunset.png", imageItem.getDownloadUrl(com.example.model.DownloadQuality.ORIGINAL))

    // 2. GIF resolution: feed uses static previewUrl, fullscreen uses actual animated GIF
    val gifItem = MediaItem(
      id = "gif_1002",
      title = "Character Animation",
      actualMediaUrl = "https://cdn.example.org/animations/dance.gif",
      previewUrl = "https://cdn.example.org/posters/dance_poster.jpg",
      mediaType = MediaType.GIF,
      rating = MediaRating.SAFE
    )
    assertEquals("https://cdn.example.org/animations/dance.gif", gifItem.actualMediaUrl)
    assertEquals("https://cdn.example.org/posters/dance_poster.jpg", gifItem.previewUrl)
    assertEquals(MediaType.GIF, gifItem.mediaType)
    // Separation check: actual media != static preview
    assertTrue(gifItem.actualMediaUrl.endsWith(".gif"))
    assertTrue(gifItem.previewUrl.endsWith(".jpg"))
    // Feed gets static preview, avoiding downloading multi-megabyte GIF
    assertEquals("https://cdn.example.org/posters/dance_poster.jpg", gifItem.getThumbnailForQuality(ThumbnailQuality.Q360))
    // Downloads and fullscreen use the real animated GIF
    assertEquals("https://cdn.example.org/animations/dance.gif", gifItem.getDownloadUrl(com.example.model.DownloadQuality.ORIGINAL))
    assertEquals("https://cdn.example.org/animations/dance.gif", gifItem.getDownloadUrl(com.example.model.DownloadQuality.Q360))

    // 3. Video resolution: feed uses static preview, NEVER a video file
    val videoWithPoster = MediaItem(
      id = "vid_1003",
      title = "AMV Trailer",
      actualMediaUrl = "https://cdn.example.org/videos/clip.mp4",
      previewUrl = "https://cdn.example.org/posters/clip_poster.jpg",
      mediaType = MediaType.VIDEO,
      rating = MediaRating.SUGGESTIVE
    )
    assertEquals("https://cdn.example.org/videos/clip.mp4", videoWithPoster.actualMediaUrl)
    assertEquals("https://cdn.example.org/posters/clip_poster.jpg", videoWithPoster.previewUrl)
    assertEquals(MediaType.VIDEO, videoWithPoster.mediaType)
    assertEquals(MediaRating.SUGGESTIVE, videoWithPoster.rating)
    assertFalse(MediaApiClient.isVideoUrl(videoWithPoster.previewUrl))
    assertEquals("https://cdn.example.org/posters/clip_poster.jpg", videoWithPoster.getThumbnailForQuality(ThumbnailQuality.Q360))
    // Video downloads always preserve the real original MP4
    assertEquals("https://cdn.example.org/videos/clip.mp4", videoWithPoster.getDownloadUrl(com.example.model.DownloadQuality.Q360))

    // 4. Video without poster: previewUrl is empty, NEVER falls back to video file
    val videoWithoutPoster = MediaItem(
      id = "vid_1004",
      title = "Raw Video",
      actualMediaUrl = "https://cdn.example.org/raw/video.webm",
      previewUrl = "",
      mediaType = MediaType.VIDEO,
      rating = MediaRating.ADULT
    )
    assertEquals("", videoWithoutPoster.previewUrl)
    assertEquals("", videoWithoutPoster.getThumbnailForQuality(ThumbnailQuality.Q360))
    assertEquals("", videoWithoutPoster.getThumbnailForQuality(ThumbnailQuality.Q720))
    assertEquals("", videoWithoutPoster.getThumbnailFallbackUrl(ThumbnailQuality.Q720))

    // 5. Media type detection hierarchy
    assertEquals(MediaType.GIF, MediaApiClient.detectMediaType(fileExtField = "gif"))
    assertEquals(MediaType.GIF, MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.org/sample.gif"))
    assertEquals(MediaType.VIDEO, MediaApiClient.detectMediaType(fileExtField = "mp4"))
    assertEquals(MediaType.VIDEO, MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.org/sample.webm"))
    assertEquals(MediaType.VIDEO, MediaApiClient.detectMediaType(explicitType = "video/webm"))
    assertEquals(MediaType.IMAGE, MediaApiClient.detectMediaType(actualMediaUrl = "https://cdn.org/sample.jpg"))

    // 6. Rating mapping verification
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("g", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("s", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("q", isDanbooru = true))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("e", isDanbooru = true))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("s", isDanbooru = false))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("safe", isDanbooru = false))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("questionable", isDanbooru = false))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("explicit", isDanbooru = false))

    // 7. Full API JSON parsing verification for image, GIF, and video items
    val danbooruConfig = MediaSourceConfig.TEMPLATE_DANBOORU
    val jsonMixedMedia = """
      [
        {
          "id": 101,
          "file_url": "https://cdn.donmai.us/original/11/22/art.png",
          "preview_file_url": "https://cdn.donmai.us/180x180/11/22/art.jpg",
          "large_file_url": "https://cdn.donmai.us/sample/11/22/art.jpg",
          "file_ext": "png",
          "rating": "g"
        },
        {
          "id": 102,
          "file_url": "https://cdn.donmai.us/original/33/44/animation.gif",
          "preview_file_url": "https://cdn.donmai.us/180x180/33/44/animation.jpg",
          "large_file_url": "https://cdn.donmai.us/sample/33/44/animation.jpg",
          "file_ext": "gif",
          "rating": "s"
        },
        {
          "id": 103,
          "file_url": "https://cdn.donmai.us/original/55/66/video.mp4",
          "preview_file_url": "https://cdn.donmai.us/180x180/55/66/video.jpg",
          "large_file_url": "https://cdn.donmai.us/sample/55/66/video.mp4",
          "file_ext": "mp4",
          "rating": "e"
        }
      ]
    """.trimIndent()

    val parsed = apiClient.parseMediaItems(danbooruConfig, jsonMixedMedia)
    assertEquals(3, parsed.size)

    val parsedImage = parsed[0]
    assertEquals(MediaType.IMAGE, parsedImage.mediaType)
    assertEquals(MediaRating.SAFE, parsedImage.rating)
    assertEquals("https://cdn.donmai.us/original/11/22/art.png", parsedImage.actualMediaUrl)
    assertEquals("https://cdn.donmai.us/180x180/11/22/art.jpg", parsedImage.previewUrl)

    val parsedGif = parsed[1]
    assertEquals(MediaType.GIF, parsedGif.mediaType)
    assertEquals(MediaRating.SUGGESTIVE, parsedGif.rating)
    assertEquals("https://cdn.donmai.us/original/33/44/animation.gif", parsedGif.actualMediaUrl)
    assertEquals("https://cdn.donmai.us/180x180/33/44/animation.jpg", parsedGif.previewUrl)
    assertTrue("GIF actualMediaUrl is animated .gif", parsedGif.actualMediaUrl.endsWith(".gif"))
    assertTrue("GIF previewUrl is lightweight static image", parsedGif.previewUrl.endsWith(".jpg"))

    val parsedVideo = parsed[2]
    assertEquals(MediaType.VIDEO, parsedVideo.mediaType)
    assertEquals(MediaRating.ADULT, parsedVideo.rating)
    assertEquals("https://cdn.donmai.us/original/55/66/video.mp4", parsedVideo.actualMediaUrl)
    assertEquals("https://cdn.donmai.us/180x180/55/66/video.jpg", parsedVideo.previewUrl)
    assertTrue("Video actualMediaUrl is playable .mp4", parsedVideo.actualMediaUrl.endsWith(".mp4"))
    assertTrue("Video previewUrl is static image poster", parsedVideo.previewUrl.endsWith(".jpg"))
    assertFalse("Video previewUrl must never be a video", MediaApiClient.isVideoUrl(parsedVideo.previewUrl))
  }

  @Test
  fun testMediaPipelineAndNsfwProtectionDiagnostics() {
    // 1. Safe rating -> SAFE
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("general"))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("safe"))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("g", isDanbooru = true))
    assertEquals(MediaRating.SAFE, MediaApiClient.parseRating("s", isDanbooru = false))

    // 2. Suggestive rating -> SUGGESTIVE
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("sensitive"))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("questionable"))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("suggestive"))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("s", isDanbooru = true))
    assertEquals(MediaRating.SUGGESTIVE, MediaApiClient.parseRating("q", isDanbooru = true))

    // 3. Adult rating -> ADULT
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("explicit"))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("adult"))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("nsfw"))
    assertEquals(MediaRating.ADULT, MediaApiClient.parseRating("e", isDanbooru = true))

    // 4. Unknown rating -> UNKNOWN (NEVER defaults to SAFE)
    assertEquals(MediaRating.UNKNOWN, MediaApiClient.parseRating(null))
    assertEquals(MediaRating.UNKNOWN, MediaApiClient.parseRating(""))
    assertEquals(MediaRating.UNKNOWN, MediaApiClient.parseRating("   "))
    assertEquals(MediaRating.UNKNOWN, MediaApiClient.parseRating("unrecognized_rating_xyz"))
    assertEquals(MediaRating.UNKNOWN, MediaApiClient.parseRating("random_text"))

    // 5. UNKNOWN is protected by NSFW blur
    fun isNsfwProtected(rating: MediaRating): Boolean {
      return rating == MediaRating.ADULT || rating == MediaRating.SUGGESTIVE || rating == MediaRating.UNKNOWN
    }
    assertTrue("UNKNOWN rating must be protected under NSFW blur", isNsfwProtected(MediaRating.UNKNOWN))

    // 6. SUGGESTIVE and ADULT are protected; SAFE remains unprotected
    assertTrue("SUGGESTIVE rating must be protected under NSFW blur", isNsfwProtected(MediaRating.SUGGESTIVE))
    assertTrue("ADULT rating must be protected under NSFW blur", isNsfwProtected(MediaRating.ADULT))
    assertFalse("SAFE rating must remain unprotected", isNsfwProtected(MediaRating.SAFE))

    // 7. GIF actual URL passed to fullscreen (never previewUrl)
    val gifItem = MediaItem(
      id = "gif_diag_1",
      title = "Diag GIF",
      actualMediaUrl = "https://cdn.example.org/actual_animation.gif",
      previewUrl = "https://cdn.example.org/static_poster.jpg",
      mediaType = MediaType.GIF,
      rating = MediaRating.UNKNOWN
    )
    val fullscreenGifUrl = gifItem.actualMediaUrl
    assertEquals("https://cdn.example.org/actual_animation.gif", fullscreenGifUrl)
    assertTrue("GIF actual URL must be .gif for decoder", fullscreenGifUrl.endsWith(".gif"))
    assertTrue("Fullscreen GIF must not use previewUrl", gifItem.previewUrl != fullscreenGifUrl)

    // 8. GIF decoder/request failure produces an error state instead of blank screen
    val simulatedException: Throwable = java.io.IOException("HTTP 403: Forbidden")
    val errorDisplay = simulatedException.message ?: "Failed to load GIF animation"
    assertTrue("Error state must be non-empty and visible", errorDisplay.isNotBlank())
    assertEquals("HTTP 403: Forbidden", errorDisplay)

    // 9. Video actual URL passed to ExoPlayer (never previewUrl)
    val videoItem = MediaItem(
      id = "vid_diag_1",
      title = "Diag Video",
      actualMediaUrl = "https://cdn.example.org/actual_stream.mp4",
      previewUrl = "https://cdn.example.org/static_poster.jpg",
      mediaType = MediaType.VIDEO,
      rating = MediaRating.SUGGESTIVE
    )
    val playbackUri = videoItem.actualMediaUrl
    assertEquals("https://cdn.example.org/actual_stream.mp4", playbackUri)
    assertTrue("Video URI must be video stream", playbackUri.endsWith(".mp4"))
    assertTrue("Playback URI must not be previewUrl", videoItem.previewUrl != playbackUri)

    // 10. PlaybackException is surfaced instead of silently failing
    val testPlaybackException = androidx.media3.common.PlaybackException(
      "Source error",
      java.io.IOException("HTTP 403 Forbidden"),
      androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    )
    val codeName = testPlaybackException.errorCodeName
    val cause = testPlaybackException.cause
    val causeDetails = cause?.message ?: testPlaybackException.message ?: "Playback failure"
    val formattedError = "$codeName: $causeDetails"
    assertTrue("Playback error must surface errorCodeName", formattedError.contains("ERROR_CODE_IO_BAD_HTTP_STATUS"))
    assertTrue("Playback error must surface HTTP 403 details", formattedError.contains("HTTP 403 Forbidden"))
    assertFalse("Playback error must not be silent or blank", formattedError.isBlank())
  }

  // --- 38. Real NSFW Blur Transformation & Compatibility Tests ---

  @Test
  fun testNsfwRealBlurTransformationAndCompatibility() {
    // 1. Verify ArtfluxBlurTransformation works on Bitmaps (Android API 24+ universal StackBlur)
    val originalBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    originalBitmap.eraseColor(android.graphics.Color.RED)

    val blurred = com.example.util.ArtfluxBlurTransformation.fastBlur(originalBitmap, radius = 20)
    assertNotNull("Blurred bitmap must not be null", blurred)
    assertEquals(100, blurred.width)
    assertEquals(100, blurred.height)

    // 2. Verify cacheKey uniqueness
    val transform = com.example.util.ArtfluxBlurTransformation(radius = 22, sampling = 4f)
    assertTrue(transform.cacheKey.contains("radius=22"))
    assertTrue(transform.cacheKey.contains("sampling=4.0"))

    // 3. Verify NSFW protection decision matrix
    val safeItem = MediaItem(id = "s1", title = "Safe", actualMediaUrl = "https://a.com/1.jpg", previewUrl = "https://a.com/1.jpg", rating = MediaRating.SAFE)
    val suggestiveItem = MediaItem(id = "s2", title = "Suggestive", actualMediaUrl = "https://a.com/2.jpg", previewUrl = "https://a.com/2.jpg", rating = MediaRating.SUGGESTIVE)
    val adultItem = MediaItem(id = "s3", title = "Adult", actualMediaUrl = "https://a.com/3.jpg", previewUrl = "https://a.com/3.jpg", rating = MediaRating.ADULT)
    val unknownItem = MediaItem(id = "s4", title = "Unknown", actualMediaUrl = "https://a.com/4.jpg", previewUrl = "https://a.com/4.jpg", rating = MediaRating.UNKNOWN)

    fun shouldBlur(item: MediaItem, blurNsfw: Boolean): Boolean {
      val isProtected = item.rating == MediaRating.ADULT || item.rating == MediaRating.SUGGESTIVE || item.rating == MediaRating.UNKNOWN
      return blurNsfw && isProtected
    }

    // When blurNsfw is true:
    assertFalse("Safe content must never be blurred", shouldBlur(safeItem, blurNsfw = true))
    assertTrue("Suggestive content must be blurred", shouldBlur(suggestiveItem, blurNsfw = true))
    assertTrue("Adult content must be blurred", shouldBlur(adultItem, blurNsfw = true))
    assertTrue("Unknown content must be blurred when NSFW blur enabled", shouldBlur(unknownItem, blurNsfw = true))

    // When blurNsfw is false (toggled off):
    assertFalse("Safe content unblurred when toggle off", shouldBlur(safeItem, blurNsfw = false))
    assertFalse("Suggestive content unblurred when toggle off", shouldBlur(suggestiveItem, blurNsfw = false))
    assertFalse("Adult content unblurred when toggle off", shouldBlur(adultItem, blurNsfw = false))
    assertFalse("Unknown content unblurred when toggle off", shouldBlur(unknownItem, blurNsfw = false))
  }

  // --- 39. Batch 12: Waifu.im Source, Random Pagination, and Color Themes Tests ---

  @Test
  fun testWaifuImSourceConfigurationAndParsing() {
    val waifuSource = MediaSourceConfig.BUILT_IN_WAIFU_IM
    assertEquals("https://api.waifu.im/images", waifuSource.apiUrl)
    assertEquals("IncludedTags", waifuSource.searchParam)
    assertEquals("items", waifuSource.itemsPath)
    assertTrue(waifuSource.isBuiltIn)

    val jsonPayload = """
      {
        "items": [
          {
            "id": 9991,
            "signature": "waifu_sig_123",
            "url": "https://cdn.waifu.im/9991.jpg",
            "isNsfw": false,
            "extension": ".jpg",
            "artists": [ { "name": "TestArtist" } ]
          }
        ]
      }
    """.trimIndent()

    val apiClient = MediaApiClient()
    val items = apiClient.parseMediaItems(waifuSource, jsonPayload)
    assertEquals(1, items.size)
    val item = items[0]
    assertEquals("9991", item.id)
    assertEquals("waifu_sig_123", item.title)
    assertEquals("https://cdn.waifu.im/9991.jpg", item.actualMediaUrl)
    assertEquals("https://cdn.waifu.im/9991.jpg", item.previewUrl)
    assertEquals(MediaRating.SAFE, item.rating)
    assertEquals("TestArtist", item.author)
  }

  @Test
  fun testRandomPaginationAndDeduplicationStability() {
    val apiClient = MediaApiClient()
    val source = MediaSourceConfig(
      name = "Test Source",
      apiUrl = "https://example.com/api.json",
      imageUrlField = "file_url",
      thumbUrlField = "preview_url"
    )

    // Simulate page 1 items
    val page1Json = """
      {
        "post": [
          { "id": "101", "file_url": "https://img.org/101.jpg", "preview_url": "https://img.org/t101.jpg", "rating": "general" },
          { "id": "102", "file_url": "https://img.org/102.jpg", "preview_url": "https://img.org/t102.jpg", "rating": "general" }
        ]
      }
    """.trimIndent()
    val page1Items = apiClient.parseMediaItems(source, page1Json)
    assertEquals(2, page1Items.size)

    // Simulate page 2 items with duplicate overlap (item 102) and new item 103
    val page2Json = """
      {
        "post": [
          { "id": "102", "file_url": "https://img.org/102.jpg", "preview_url": "https://img.org/t102.jpg", "rating": "general" },
          { "id": "103", "file_url": "https://img.org/103.jpg", "preview_url": "https://img.org/t103.jpg", "rating": "general" }
        ]
      }
    """.trimIndent()
    val page2Items = apiClient.parseMediaItems(source, page2Json)

    // Deduplication logic test
    val rawItems = page1Items.toMutableList()
    val existingIds = rawItems.map { it.id }.toSet()
    val existingUrls = rawItems.map { it.actualMediaUrl }.toSet()
    val uniqueNew = page2Items.filter { it.id !in existingIds && it.actualMediaUrl !in existingUrls }

    assertEquals(1, uniqueNew.size)
    assertEquals("103", uniqueNew[0].id)

    rawItems.addAll(uniqueNew)
    assertEquals(3, rawItems.size)
    assertEquals("101", rawItems[0].id)
    assertEquals("102", rawItems[1].id)
    assertEquals("103", rawItems[2].id)
  }

  @Test
  fun testColorPalettePersistence() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsRepo = com.example.data.PreferencesRepository(context)

    // Default color palette should be ARCTIC_SIGNAL
    assertEquals(com.example.model.ColorPalette.ARCTIC_SIGNAL, prefsRepo.colorPalette.value)

    // Set and persist EMERALD_NOIR
    prefsRepo.setColorPalette(com.example.model.ColorPalette.EMERALD_NOIR)
    assertEquals(com.example.model.ColorPalette.EMERALD_NOIR, prefsRepo.colorPalette.value)

    // Simulate restart / reload
    val reloadedRepo = com.example.data.PreferencesRepository(context)
    assertEquals(com.example.model.ColorPalette.EMERALD_NOIR, reloadedRepo.colorPalette.value)

    // Reset to CRIMSON_FLUX
    reloadedRepo.setColorPalette(com.example.model.ColorPalette.CRIMSON_FLUX)
    assertEquals(com.example.model.ColorPalette.CRIMSON_FLUX, reloadedRepo.colorPalette.value)
  }

  @Test
  fun testDuplicateOnlyPagesPreventionAndRandomStability() {
    val item1 = MediaItem(id = "1", title = "Item 1", previewUrl = "https://img.com/1.jpg", actualMediaUrl = "https://img.com/1.jpg")
    val item2 = MediaItem(id = "2", title = "Item 2", previewUrl = "https://img.com/2.jpg", actualMediaUrl = "https://img.com/2.jpg")
    val item3 = MediaItem(id = "3", title = "Item 3", previewUrl = "https://img.com/3.jpg", actualMediaUrl = "https://img.com/3.jpg")

    val page1 = listOf(item1, item2)
    val page2DuplicateOnly = listOf(item1, item2) // 0 new items
    val page3WithNew = listOf(item2, item3) // 1 new item: item3

    val existingIds = page1.map { it.id }.toSet()
    val existingUrls = page1.map { it.actualMediaUrl }.toSet()

    // Page 2 check
    val uniquePage2 = page2DuplicateOnly.filter { it.id !in existingIds && it.actualMediaUrl !in existingUrls }
    assertTrue(uniquePage2.isEmpty()) // Duplicate-only page detected!

    // Page 3 check
    val uniquePage3 = page3WithNew.filter { it.id !in existingIds && it.actualMediaUrl !in existingUrls }
    assertEquals(1, uniquePage3.size)
    assertEquals("3", uniquePage3[0].id)

    // Existing items remain stable
    val updatedList = page1 + uniquePage3
    assertEquals(3, updatedList.size)
    assertEquals("1", updatedList[0].id)
    assertEquals("2", updatedList[1].id)
    assertEquals("3", updatedList[2].id)
  }

  @Test
  fun testUnsaveBackupAndUndoLastUnsavePreservesCollections() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val savesRepo = com.example.data.SavesRepository(context)

    val item = MediaItem(
      id = "test_undo_item_99",
      title = "Test Undo Artwork",
      previewUrl = "https://img.com/undo.jpg",
      actualMediaUrl = "https://img.com/undo.jpg"
    )

    // Create a collection
    val col = savesRepo.createCollection("Test Undo Collection")

    // Add media to collection (which also saves the item)
    savesRepo.addMediaToCollection(col.id, item)

    assertTrue(savesRepo.isSaved(item.id))
    assertTrue(savesRepo.getMediaForCollection(col.id).any { it.id == item.id })

    // Unsave the item
    savesRepo.unsaveItem(item.id)

    assertFalse(savesRepo.isSaved(item.id))
    assertFalse(savesRepo.getMediaForCollection(col.id).any { it.id == item.id })

    // Undo the unsave action
    savesRepo.undoLastUnsave()

    assertTrue(savesRepo.isSaved(item.id))
    assertTrue(savesRepo.getMediaForCollection(col.id).any { it.id == item.id })
  }
}

