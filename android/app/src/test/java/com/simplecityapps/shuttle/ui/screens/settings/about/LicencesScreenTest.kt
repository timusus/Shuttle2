package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicencesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedWebsites = mutableListOf<String>()

    private fun setContent(uiState: LicencesUiState) {
        composeTestRule.setContent {
            S2AppTheme(AppThemeState()) {
                LicencesScreen(uiState = uiState, onNavigateUp = {}, onOpenWebsite = { openedWebsites += it })
            }
        }
    }

    @Test
    fun `each library shows its version and licence, and one with a website opens it`() {
        setContent(
            LicencesUiState(
                licences = listOf(
                    Licence(name = "Media3", version = "1.8.0", licence = "Apache 2.0", website = "https://developer.android.com/media"),
                    Licence(name = "TagLib", version = null, licence = "LGPL 2.1", website = null)
                ),
                loading = false
            )
        )

        composeTestRule.onNodeWithText("1.8.0 · Apache 2.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("LGPL 2.1").assertIsDisplayed()
        composeTestRule.onNodeWithText("TagLib").performClick()
        composeTestRule.onNodeWithText("Media3").performClick()

        openedWebsites shouldBe listOf("https://developer.android.com/media")
    }

    @Test
    fun `a library with nothing known about it says so`() {
        setContent(LicencesUiState(licences = listOf(Licence(name = "Mystery", version = null, licence = null, website = null)), loading = false))

        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()
    }
}
