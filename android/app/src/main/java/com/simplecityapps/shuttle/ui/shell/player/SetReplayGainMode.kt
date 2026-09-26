package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import javax.inject.Inject

/** Stores the ReplayGain mode and applies it to the live audio processor, as the Settings screen's choice does. */
class SetReplayGainMode @Inject constructor(
    private val saveSetting: SaveSetting,
    private val settingsEffects: SettingsEffects,
) {
    operator fun invoke(mode: ReplayGainMode) {
        saveSetting(PlaybackSettings.ReplayGain, mode)
        settingsEffects.onSettingChanged(PlaybackSettings.ReplayGain, mode)
    }
}
