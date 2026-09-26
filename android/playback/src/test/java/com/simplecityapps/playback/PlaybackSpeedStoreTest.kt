package com.simplecityapps.playback

import androidx.media3.common.PlaybackParameters
import com.simplecityapps.playback.fakes.FakeListenedPlayer
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackSpeedStoreTest {
    private val player = FakeListenedPlayer()

    private val playbackSpeed = PlaybackSettings(SettingsStore(FakeSharedPreferences())).playbackSpeed

    private val store = PlaybackSpeedStore(player, playbackSpeed)

    @Test
    fun `restore puts the saved speed back, keeping the pitch`() {
        playbackSpeed.value = 1.5f

        store.restore()

        player.parameters shouldBe PlaybackParameters(1.5f)
    }

    @Test
    fun `restore leaves a player at normal speed alone when normal speed was saved`() {
        val untouched = PlaybackParameters(1f, 0.8f)
        player.parameters = untouched

        store.restore()

        player.parameters shouldBe untouched
    }

    @Test
    fun `a speed change on this device is saved, and one on a Cast receiver isn't`() {
        store.onPlaybackParametersChanged(PlaybackParameters(1.25f))
        playbackSpeed.value shouldBe 1.25f

        player.device = FakeListenedPlayer.REMOTE
        store.onPlaybackParametersChanged(PlaybackParameters(2f))
        playbackSpeed.value shouldBe 1.25f
    }
}
