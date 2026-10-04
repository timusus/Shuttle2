package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.PendingEvent
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The snackbar that tells the user a server signed them out, and offers to sign in again (#595). */
@RunWith(RobolectricTestRunner::class)
class ShellEventsEffectTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val handled = mutableListOf<Long>()
    private val signIns = mutableListOf<MediaProviderType>()

    private fun showSignedOut(type: MediaProviderType) {
        composeTestRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            ShellEventsEffect(
                events = listOf(PendingEvent(1L, ShellEvent.ServerSignedOut(type))),
                onEventHandled = handled::add,
                snackbarHostState = snackbarHostState,
                onSignIn = signIns::add,
            )
            SnackbarHost(snackbarHostState)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `names the server that signed the user out and offers to sign in`() {
        showSignedOut(MediaProviderType.Plex)

        composeTestRule.onNodeWithText("Signed out of Plex").assertExists()
        composeTestRule.onNodeWithText("Sign in").assertExists()
    }

    @Test
    fun `Sign in opens that server's sign-in dialog`() {
        showSignedOut(MediaProviderType.Jellyfin)

        composeTestRule.onNodeWithText("Sign in").performClick()
        composeTestRule.waitUntil { signIns.isNotEmpty() }

        signIns shouldBe listOf(MediaProviderType.Jellyfin)
        composeTestRule.onAllNodes(hasText("Signed out of Jellyfin")).fetchSemanticsNodes().size shouldBe 0
    }
}
