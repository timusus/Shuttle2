package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.shared.playback.FakeIosAudioPlayer
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A ReplayGain change reaches the player as it's made, so the song playing takes it, as on Android. */
class IosSettingsEffectsTest {
    private val player = FakeIosAudioPlayer()
    private val playbackSettings = PlaybackSettings(SettingsStore(InMemoryKeyValueStore()))
    private val effects = IosSettingsEffects(NoMediaSources, player, playbackSettings)

    @Test
    fun aModeChangeGoesToThePlayerWithTheSavedPreamp() {
        playbackSettings.preAmpGain.value = 3f
        playbackSettings.replayGainMode.value = ReplayGainMode.Album

        effects.onSettingChanged(PlaybackSettings.ReplayGain, ReplayGainMode.Album)

        player.replayGains shouldContainExactly listOf(ReplayGainMode.Album to 3f)
    }

    @Test
    fun aPreampChangeGoesToThePlayerWithTheSavedMode() {
        playbackSettings.replayGainMode.value = ReplayGainMode.Track
        playbackSettings.preAmpGain.value = -2f

        effects.onSettingChanged(PlaybackSettings.PreAmpGain, -2f)

        player.replayGains shouldContainExactly listOf(ReplayGainMode.Track to -2f)
    }

    @Test
    fun anotherSettingSendsNothing() {
        effects.onSettingChanged(PlaybackSettings.RetainShuffleOnNewQueue, true)

        player.replayGains shouldContainExactly emptyList()
    }

    private object NoMediaSources : MediaSources {
        override val enabledTypes: StateFlow<List<MediaProviderType>> = MutableStateFlow(emptyList())
        override val hasScanned = false
        override val songTagsOutdated = false

        override fun enable(type: MediaProviderType) = Unit

        override fun disable(type: MediaProviderType) = Unit

        override fun scan(foldersChanged: Boolean) = Unit

        override fun syncIfStale() = Unit
    }
}
