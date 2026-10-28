package com.example

import android.app.DownloadManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.*
import com.example.model.*
import com.example.ui.*
import com.example.ui.components.LightboxViewer
import com.example.ui.theme.ArtfluxTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HealthStorageViewerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun item(id: String, tags: List<String> = listOf("sky")) = MediaItem(id, "Artwork $id", "file:///missing-$id.png", "", tags = tags)

    @Test fun sourceHealthShowsRecoveryAndHonorsCooldown() {
        val source = MediaSourceConfig.BUILT_IN_SAFEBOORU
        var checked = false
        compose.setContent { ArtfluxTheme { SourceHealthSheet(listOf(source), mapOf(source.id to SourceHealth(SourceCondition.RATE_LIMITED,
            System.currentTimeMillis(), retryAt = System.currentTimeMillis() + 60000)), emptySet(), { checked = true }, {}, {}, {}) } }
        compose.onNodeWithTag("check_health_${source.id}").assertIsNotEnabled()
        compose.onNodeWithText("Rate limited").assertIsDisplayed()
        assertFalse(checked)
        capture("source-health")
    }

    @Test fun offlineRemovalRequiresConfirmationAndIncludesOnlySelectedPrivateRecords() {
        val records = listOf(ArtworkDownload(1, item("one"), true, DownloadManager.STATUS_SUCCESSFUL, 1024, 1024, localUri = "content://one"),
            ArtworkDownload(2, item("two"), true, DownloadManager.STATUS_FAILED, fileMissing = true), ArtworkDownload(3, item("gallery"), false))
        var removed = emptySet<Long>()
        compose.setContent { ArtfluxTheme { OfflineStorageSheet(records, OfflineStorageSummary(1024, 100000, 1, true), {}, { removed = it }, {}, {}) } }
        compose.onNodeWithTag("offline_select_1").performClick()
        capture("offline-storage")
        compose.onNodeWithTag("remove_selected_offline").performClick()
        assertTrue(removed.isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("remove_selected_offline").performClick()
        compose.onNodeWithTag("confirm_remove_offline").performClick()
        assertEquals(setOf(1L), removed)
        compose.onNodeWithTag("offline_select_3").assertDoesNotExist()
    }

    @Test fun relatedSheetOpensTheChosenResultAndShowsMetadataMeaning() {
        val seed = item("seed").copy(author = "Artist")
        val match = item("match")
        var opened: MediaItem? = null
        var artistSelected = false
        compose.setContent { ArtfluxTheme { RelatedArtworkSheet(RelatedArtworkState(seed, null, listOf("sky"), "sky", FilterState(), localItems = listOf(match)),
            ThumbnailQuality.Q720, false, {}, {}, { opened = it }, {}, onArtist = { artistSelected = true }) } }
        compose.onNodeWithTag("media_item_card_match").performClick()
        assertEquals(match, opened)
        compose.onNodeWithTag("related_artist").performClick()
        assertTrue(artistSelected)
        capture("related-artwork")
    }

    @Test fun viewerButtonsUseDisplayedArtworkAndNavigateAtSmallWidth() {
        val items = listOf(item("one"), item("two"))
        var index = 0
        var downloaded: MediaItem? = null
        var related: MediaItem? = null
        compose.setContent { ArtfluxTheme { Surface(Modifier.fillMaxSize()) {
            LightboxViewer(items[0], 0, 2, items, onIndexChanged = { index = it }, onClose = {}, onDownload = {}, onTagClick = {},
                onToggleSave = {}, onDownloadItem = { downloaded = it }, onExploreRelated = { item, _ -> related = item })
        } } }
        compose.onNodeWithTag("viewer_previous").assertIsNotEnabled()
        compose.onNodeWithTag("viewer_next").performClick()
        compose.waitForIdle()
        assertEquals(1, index)
        compose.onNodeWithTag("viewer_next").assertIsNotEnabled()
        compose.onNodeWithTag("lightbox_download_button").performClick()
        assertEquals(items[1], downloaded)
        compose.onNodeWithTag("viewer_related").performClick()
        assertEquals(items[1], related)
        capture("viewer-controls")
    }

    @Test fun imageDoubleTapCanBeResetAndControlsCanBeHiddenAndRestored() {
        val artwork = localImage("zoom")
        compose.setContent { ArtfluxTheme { Surface(Modifier.fillMaxSize()) {
            LightboxViewer(artwork, 0, 1, onClose = {}, onDownload = {}, onTagClick = {})
        } } }
        compose.onNodeWithTag("viewer_image_surface").performTouchInput { doubleClick(Offset(12f, 12f)) }
        compose.onNodeWithTag("viewer_reset_zoom").assertIsDisplayed().performClick()
        compose.onNodeWithTag("viewer_reset_zoom").assertDoesNotExist()
        compose.onNodeWithTag("viewer_image_surface").performTouchInput { click(Offset(12f, 12f)) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("viewer_show_controls").assertIsDisplayed().performClick()
        compose.onNodeWithTag("lightbox_close_button").assertIsDisplayed()
    }

    private fun localImage(id: String): MediaItem {
        val file = File(compose.activity.cacheDir, "$id.png")
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return item(id).copy(actualMediaUrl = file.toURI().toString(), width = 200, height = 300)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/reports/ui").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
