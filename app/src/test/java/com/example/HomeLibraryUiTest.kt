package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.model.*
import com.example.ui.*
import com.example.ui.components.ArtfluxBottomBar
import com.example.ui.theme.ArtfluxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val artworks = (1..4).map { MediaItem("$it", "Artwork $it", "android.resource://com.example/drawable/artflux_icon", "android.resource://com.example/drawable/artflux_icon", width = 300, height = if (it % 2 == 0) 360 else 440) }

    @Test fun midnightHome() { renderHome(ColorPalette.ARCTIC_SIGNAL); capture("home-midnight") }
    @Test fun slateHome() { renderHome(ColorPalette.EMERALD_NOIR); capture("home-slate") }
    @Test fun paperHome() { renderHome(ColorPalette.CRIMSON_FLUX); capture("home-paper") }

    @Test fun searchExpandsInHomeAndSubmitsWithoutSearchTab() {
        var draft by mutableStateOf("")
        var submitted = ""
        compose.setContent { ArtfluxTheme {
            Scaffold(bottomBar = { ArtfluxBottomBar(MainTab.HOME) {} }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
                HomeHub(FeedState(searchQuery = draft, hasReachedEnd = true), BrowsingPreferences(), rememberLazyStaggeredGridState(), ThumbnailQuality.Q720, false,
                    onQueryChange = { draft = it }, onSearch = { submitted = it }, onSources = {}, onFilters = {}, onFilterChange = {}, onRefresh = {}, onMore = {},
                    onOpen = {}, onDownload = {}, onSaveSearch = {}, onSavedSearch = {}, onRemoveSavedSearch = {}, onClearHistory = {})
            } }
        } }
        compose.onNodeWithTag("home_search_button").performClick()
        compose.onNodeWithTag("home_search_field").performTextInput("landscape")
        compose.onNodeWithContentDescription("Submit search").performClick()
        assertEquals("landscape", submitted)
        compose.onNodeWithTag("nav_tab_library").assertIsDisplayed()
        compose.onNodeWithTag("nav_tab_search").assertDoesNotExist()
    }

    @Test fun librarySearchFiltersSavedArtwork() {
        compose.setContent { ArtfluxTheme { Scaffold(bottomBar = { ArtfluxBottomBar(MainTab.LIBRARY) {} }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
            LibraryHub(artworks, listOf(ArtCollection("col", "Inspiration")), emptyList(), BrowsingPreferences(), ThumbnailQuality.Q720, false,
                onOpen = { _, _, _ -> }, onCreate = {}, onRename = { _, _ -> }, onDelete = {}, onAdd = { _, _ -> }, onRemove = {},
                onDownload = { _, _ -> }, onRetry = {}, onRemoveDownload = {})
        } } } }
        capture("library-midnight")
        compose.onNodeWithTag("library_search").performTextInput("Artwork 2")
        compose.onNodeWithTag("media_item_card_2").assertIsDisplayed()
        compose.onNodeWithTag("media_item_card_1").assertDoesNotExist()
    }

    private fun renderHome(palette: ColorPalette) {
        compose.setContent { ArtfluxTheme(colorPalette = palette) { Scaffold(bottomBar = { ArtfluxBottomBar(MainTab.HOME) {} }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
            HomeHub(FeedState(mediaItems = artworks, hasReachedEnd = true), BrowsingPreferences(), rememberLazyStaggeredGridState(), ThumbnailQuality.Q720, false,
                onQueryChange = {}, onSearch = {}, onSources = {}, onFilters = {}, onFilterChange = {}, onRefresh = {}, onMore = {},
                onOpen = {}, onDownload = {}, onSaveSearch = {}, onSavedSearch = {}, onRemoveSavedSearch = {}, onClearHistory = {})
        } } } }
        compose.onNodeWithTag("home_search_button").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Draw the native-rendered view directly; PixelCopy's force-redraw callback is
        // unavailable in this Robolectric setup, though layout/interaction tests work.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/ui").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
