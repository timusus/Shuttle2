package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import com.simplecityapps.shuttle.ui.screens.settings.ShareDebugLogsResult
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * What an iOS settings change does beyond storing it. ReplayGain and its preamp go to the player, which applies them to
 * the song playing, as Android's `AndroidSettingsEffects` does. Every other row [IosSettingsCatalog] shows is read where
 * it's used (the queue's shuffle rule, each stream's bitrate cap, each artwork url), so needs no push. A rescan imports
 * as Sources' Scan Now does. The artwork and debug-log actions aren't in the iOS catalog, so nothing calls them: they do
 * nothing rather than pretend.
 */
@ContributesBinding(AppScope::class)
class IosSettingsEffects @Inject constructor(
    private val mediaSources: MediaSources,
    private val player: IosAudioPlayer,
    private val playbackSettings: PlaybackSettings
) : SettingsEffects {
    override fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    ) {
        when (setting) {
            PlaybackSettings.ReplayGain -> player.setReplayGain(value as ReplayGainMode, playbackSettings.preAmpGain.value)
            PlaybackSettings.PreAmpGain -> player.setReplayGain(playbackSettings.replayGainMode.value, value as Float)
        }
    }

    override fun rescan() = mediaSources.scan()

    override suspend fun clearArtworkCache() = Unit

    override fun downloadAllArtwork() = Unit

    override suspend fun shareDebugLogs(): ShareDebugLogsResult = ShareDebugLogsResult.Empty
}
