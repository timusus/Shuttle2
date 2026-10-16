package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject
import kotlin.time.Instant

/** When the library was last scanned, if it has been. */
class ReadLastScanDate @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Instant? = generalPreferenceManager.lastMediaImportDate
}

/** The equalizer preset in use now. */
class ReadEqualizerPreset @Inject constructor(
    private val equalizerPresetStore: EqualizerPresetStore
) {
    operator fun invoke(): Equalizer.Presets.Preset = equalizerPresetStore.preset
}
