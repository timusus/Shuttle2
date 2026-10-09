package com.simplecityapps.shuttle.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ListenBrainzUiState
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ScrobblingScreen
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ScrobblingUiState
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class ScrobblingScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private val account = mutableStateOf<LastFmAccountState>(LastFmAccountState.SignedOut)
    private var finishes = 0

    private fun setContent() {
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { Screen() }
        }
    }

    @Composable
    private fun Screen() {
        ScrobblingScreen(
            uiState = ScrobblingUiState(account = account.value),
            onNavigateUp = {},
            onSignIn = {},
            onFinishSignIn = { finishes++ },
            onSignOut = {},
            onServerStreamsChange = {},
            onApprovalUrlOpened = {},
            onMessageShown = {},
            listenBrainz = ListenBrainzUiState(),
            onListenBrainzSignIn = {},
            onListenBrainzSignOut = {},
            onListenBrainzMessageShown = {}
        )
    }

    private fun move(state: Lifecycle.State) {
        composeTestRule.runOnUiThread { owner.registry.currentState = state }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `entering awaiting approval while resumed does not finish the sign-in`() {
        setContent()

        account.value = LastFmAccountState.AwaitingApproval
        composeTestRule.waitForIdle()

        finishes shouldBe 0
    }

    @Test
    fun `returning from the browser finishes the sign-in once`() {
        setContent()
        account.value = LastFmAccountState.AwaitingApproval
        composeTestRule.waitForIdle()

        move(Lifecycle.State.CREATED)
        move(Lifecycle.State.RESUMED)

        finishes shouldBe 1
    }

    @Test
    fun `a pause without the app being stopped does not finish the sign-in`() {
        setContent()
        account.value = LastFmAccountState.AwaitingApproval
        composeTestRule.waitForIdle()

        move(Lifecycle.State.STARTED)
        move(Lifecycle.State.RESUMED)

        finishes shouldBe 0
    }

    @Test
    fun `resuming without having left while awaiting does nothing`() {
        setContent()
        move(Lifecycle.State.CREATED)
        account.value = LastFmAccountState.AwaitingApproval
        composeTestRule.waitForIdle()
        move(Lifecycle.State.RESUMED)

        finishes shouldBe 0
    }

    @Test
    fun `a stop caused by a configuration change does not finish the sign-in on resume`() {
        setContent()
        account.value = LastFmAccountState.AwaitingApproval
        composeTestRule.waitForIdle()

        // What the activity reports while it is being recreated for a rotation, dark-mode or locale change.
        setChangingConfigurations(true)
        move(Lifecycle.State.CREATED)
        setChangingConfigurations(false)
        move(Lifecycle.State.RESUMED)

        finishes shouldBe 0
    }

    private fun setChangingConfigurations(changing: Boolean) {
        ReflectionHelpers.setField(composeTestRule.activity, "mChangingConfigurations", changing)
    }
}
