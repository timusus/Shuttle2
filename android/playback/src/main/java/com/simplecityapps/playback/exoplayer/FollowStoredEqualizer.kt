package com.simplecityapps.playback.exoplayer

import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.equalizer.restorePreset
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * Keeps this equalizer in step with the stored one (its switch, preamp, preset and Custom band gains), so a change
 * made outside the equalizer screen, such as a restored backup, reaches the audio. The screen sets the equalizer as
 * a slider moves and then stores it, so applying the stored value again changes nothing. [scope] runs on the main
 * thread, where the equalizer is set.
 */
fun EqualizerAudioProcessor.followStoredSettings(
    settings: EqualizerSettings,
    presetStore: EqualizerPresetStore,
    store: KeyValueStore,
    scope: CoroutineScope
) {
    settings.enabled.flow.onEach { enabled = it }.launchIn(scope)
    settings.preampGain.flow.onEach { preampGainDb = it }.launchIn(scope)
    merge(store.changes(KeyValueEqualizerPresetStore.PresetKey), store.changes(KeyValueEqualizerPresetStore.CustomPresetBandsKey))
        .onEach { preset = presetStore.restorePreset() }
        .launchIn(scope)
}
