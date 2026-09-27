package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The equalizer's switch and preamp. Its preset and band gains are editor state, kept in an `EqualizerPresetStore`. */
@SingleIn(AppScope::class)
class EqualizerSettings @Inject constructor(
    store: SettingsStore
) {
    val enabled = store.preference(Enabled)
    val preampGain = store.preference(PreampGain)

    companion object {
        val Enabled = Setting.boolean("equalizer_enabled", false)

        /** The equalizer's preamp, in dB, within ±`EqualizerControl.maxPreampGain`. */
        val PreampGain = Setting.float("equalizer_preamp_gain", 0f)
    }
}
