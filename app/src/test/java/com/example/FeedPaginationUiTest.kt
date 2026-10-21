package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FeedPaginationFooter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedPaginationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedPageOffersExplicitRetry() {
        var retries = 0
        compose.setContent { MaterialTheme { FeedPaginationFooter(false, "Offline", { retries++ }) } }
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry loading more").performClick()
        assertEquals(1, retries)
    }

    @Test fun emptyFilteredFeedStillOffersMorePages() {
        var loads = 0
        compose.setContent { MaterialTheme { EmptyStateView(true, {}, { loads++ }) } }
        compose.onNodeWithText("Load more").performClick()
        assertEquals(1, loads)
    }
}
