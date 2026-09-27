package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import dev.zacsweers.metro.Inject

/** Stores the ReplayGain mode and applies it to the live audio processor, as the Settings screen's choice does. */
class SetReplayGainMode @Inject constructor(
    private val saveSetting: SaveSetting,
    private val settingsEffects: SettingsEffects,
    private val replayGainModeSetting: ReplayGainModeSetting,
) {
    operator fun invoke(mode: ReplayGainMode) {
        val setting = replayGainModeSetting.setting()
        saveSetting(setting, mode)
        settingsEffects.onSettingChanged(setting, mode)
    }
}
