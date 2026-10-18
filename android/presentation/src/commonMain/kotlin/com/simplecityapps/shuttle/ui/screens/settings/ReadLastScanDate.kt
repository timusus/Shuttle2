package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.shuttle.persistence.SourceStateStore
import dev.zacsweers.metro.Inject
import kotlin.time.Instant

/** When the library was last scanned, if it has been. */
class ReadLastScanDate @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke(): Instant? = sourceState.lastMediaImportDate
}

class ReadEqualizerPreset @Inject constructor(
    private val equalizerPresetStore: EqualizerPresetStore
) {
    operator fun invoke(): Equalizer.Presets.Preset = equalizerPresetStore.preset
}
