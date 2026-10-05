package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.TryUseProFeature
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import dev.zacsweers.metro.Inject

/**
 * Stores the ReplayGain mode and applies it to the live audio processor, as the Settings screen's choice does.
 * Turning ReplayGain on (Track or Album) is a Shuttle Music Pro feature; turning it off stays free, so nobody is
 * stuck with a mode they can no longer change. A refusal leaves the stored mode alone (the gate has asked for the
 * upgrade).
 */
class SetReplayGainMode @Inject constructor(
    private val saveSetting: SaveSetting,
    private val settingsEffects: SettingsEffects,
    private val replayGainModeSetting: ReplayGainModeSetting,
    private val tryUseProFeature: TryUseProFeature,
) {
    suspend operator fun invoke(mode: ReplayGainMode) {
        if (mode != ReplayGainMode.Off && !tryUseProFeature(ProFeature.AdvancedAudio)) return
        val setting = replayGainModeSetting.setting()
        saveSetting(setting, mode)
        settingsEffects.onSettingChanged(setting, mode)
    }
}
