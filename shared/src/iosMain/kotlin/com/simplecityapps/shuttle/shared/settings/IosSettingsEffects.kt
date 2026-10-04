package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import com.simplecityapps.shuttle.ui.screens.settings.ShareDebugLogsResult
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * What an iOS settings change does beyond storing it. Every row [IosSettingsCatalog] shows is read where it's used
 * (the queue's shuffle rule, each stream's bitrate cap and ReplayGain, each artwork url), so a change needs no push:
 * ReplayGain and its preamp apply from the next song resolved, as Android's apply from the next song. A rescan
 * imports as Sources' Scan Now does. The artwork and debug-log actions aren't in the iOS catalog, so nothing calls
 * them: they do nothing rather than pretend.
 */
@ContributesBinding(AppScope::class)
class IosSettingsEffects @Inject constructor(
    private val mediaSources: MediaSources
) : SettingsEffects {
    override fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    ) = Unit

    override fun rescan() = mediaSources.scan()

    override suspend fun clearArtworkCache() = Unit

    override fun downloadAllArtwork() = Unit

    override suspend fun shareDebugLogs(): ShareDebugLogsResult = ShareDebugLogsResult.Empty
}
