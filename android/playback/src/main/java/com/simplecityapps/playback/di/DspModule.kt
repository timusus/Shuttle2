package com.simplecityapps.playback.di

import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.equalizer.EqualizerControl
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.equalizer.restorePreset
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.exoplayer.followStoredSettings
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesTo(AppScope::class)
@BindingContainer
class DspModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideEqualizer(
        playbackPreferenceManager: PlaybackPreferenceManager,
        equalizerSettings: EqualizerSettings,
        keyValueStore: KeyValueStore,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): EqualizerAudioProcessor = EqualizerAudioProcessor(equalizerSettings.enabled.value, equalizerSettings.preampGain.value).apply {
        preset = playbackPreferenceManager.restorePreset()
        followStoredSettings(equalizerSettings, playbackPreferenceManager, keyValueStore, appCoroutineScope)
    }

    @Provides
    fun provideEqualizerControl(equalizer: EqualizerAudioProcessor): EqualizerControl = equalizer

    @Provides
    fun provideEqualizerPresetStore(playbackPreferenceManager: PlaybackPreferenceManager): EqualizerPresetStore = playbackPreferenceManager

    @SingleIn(AppScope::class)
    @Provides
    fun provideReplayGainAudioProcessor(
        playbackSettings: PlaybackSettings,
        appearanceSettings: AppearanceSettings,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): ReplayGainAudioProcessor = ReplayGainAudioProcessor(playbackSettings.replayGainMode.value, playbackSettings.preAmpGain.value.toDouble()).also { processor ->
        processor.albumGainForWorks = appearanceSettings.classicalDisplay.value
        appCoroutineScope.launch { appearanceSettings.classicalDisplay.flow.collect { processor.albumGainForWorks = it } }
    }
}
