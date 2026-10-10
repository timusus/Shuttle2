package com.simplecityapps.playback

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidPlaybackServiceStarterTest {
    private val application = RuntimeEnvironment.getApplication()
    private val starter = AndroidPlaybackServiceStarter(application)

    @Test
    fun `each action maps to its service intent action`() {
        val expected = mapOf(
            PlaybackServiceAction.TogglePlayback to PlaybackService.ACTION_TOGGLE_PLAYBACK,
            PlaybackServiceAction.SkipPrevious to PlaybackService.ACTION_SKIP_PREV,
            PlaybackServiceAction.SkipNext to PlaybackService.ACTION_SKIP_NEXT,
            PlaybackServiceAction.ToggleShuffle to PlaybackService.ACTION_TOGGLE_SHUFFLE,
            PlaybackServiceAction.ToggleRepeat to PlaybackService.ACTION_TOGGLE_REPEAT,
            PlaybackServiceAction.ShuffleAll to PlaybackService.ACTION_SHUFFLE_ALL
        )
        PlaybackServiceAction.entries.forEach { AndroidPlaybackServiceStarter.intentAction(it) shouldBe expected.getValue(it) }
    }

    @Test
    fun `start sends the action to the playback service`() {
        starter.start(PlaybackServiceAction.SkipNext)

        val intent = shadowOf(application).nextStartedService
        intent.action shouldBe PlaybackService.ACTION_SKIP_NEXT
        intent.component?.className shouldBe PlaybackService::class.java.name
    }
}
