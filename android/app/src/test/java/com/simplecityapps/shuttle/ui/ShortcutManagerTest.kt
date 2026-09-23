package com.simplecityapps.shuttle.ui

import android.app.Application
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShortcutManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = RuntimeEnvironment.getApplication()
    private val playbackManager = FakePlaybackManager()
    private val shortcutHelper = mockk<ShortcutHelper>(relaxed = true)
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val shortcutManager = ShortcutManager(application, playbackManager, shortcutHelper, appCoroutineScope)

    @After
    fun tearDown() {
        appCoroutineScope.cancel()
    }

    @Test
    fun `registering creates the shortcut from the current state without updating it`() {
        playbackManager.playbackStateFlow.value = PlaybackState.Playing

        shortcutManager.registerCallbacks()

        verify(exactly = 1) { shortcutHelper.createPlaybackShortcut(application, true) }
        verify(exactly = 0) { shortcutHelper.updatePlaybackShortcut(any(), any()) }
    }

    @Test
    fun `playback state changes update the shortcut`() {
        shortcutManager.registerCallbacks()

        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        playbackManager.playbackStateFlow.value = PlaybackState.Paused

        verify(exactly = 1) { shortcutHelper.updatePlaybackShortcut(application, true) }
        verify(exactly = 1) { shortcutHelper.updatePlaybackShortcut(application, false) }
    }
}
