package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsGroup
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsScreen
import com.simplecityapps.shuttle.ui.screens.settings.model.StreamingQualityOptions
import com.simplecityapps.shuttle.ui.text.StringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/**
 * iOS's settings: only the rows something on iOS acts on, so no row is a switch that does nothing. Swift's
 * `SettingsView` renders it as one grouped `Form`, with a Sources row of its own above the Sources rows.
 *
 * Left out, with why (`docs/architecture/ios-port/phase-5-ios-app.md`, "Settings"):
 * - Appearance: theme, dynamic colour, accent, colour from artwork and pure black restyle Android's Material
 *   theme, which iOS doesn't draw; widget opacity has no widget; show Home on launch, until the iOS shell reads
 *   `ShellViewModel`'s start tab.
 * - Playback & sound: USB DAC direct output is Android's mixer; the equalizer, ReplayGain and preamp wait for the
 *   engine's DSP (phase 6, #604).
 * - Sources: reporting playback to the server has no iOS reporter yet; download on Wi-Fi only, until downloads.
 * - Library: rescan frequency needs a background scheduler; excluded songs and folders wait for local files (phase
 *   8); artwork Wi-Fi only gates the S2 artwork service iOS doesn't use; media session artwork, clearing the
 *   artwork cache and downloading all artwork reach Android's Coil and media session.
 * - Privacy: iOS has no crash reporting or analytics.
 * - About: What's New and Licences read Android's bundled changelog and `aboutlibraries.json`; iOS's
 *   acknowledgements are in its Settings bundle. File logging and debug logs have no iOS log file.
 */
object IosSettingsCatalog : SettingsCatalog {
    val playbackAndSound = SettingsScreen(
        destination = SettingsDestination.PlaybackAndSound,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = PlaybackSettings.RetainShuffleOnNewQueue,
                        title = StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_TITLE,
                        summary = StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_SUBTITLE
                    )
                )
            )
        )
    )

    val sources = SettingsScreen(
        destination = SettingsDestination.Sources,
        groups = listOf(
            SettingsGroup(
                title = StringKey.PREF_CATEGORY_TITLE_STREAMING_QUALITY,
                items = listOf(
                    SettingItem.Choice(
                        setting = StreamingSettings.UnmeteredQuality,
                        title = StringKey.PREF_STREAMING_QUALITY_UNMETERED_TITLE,
                        options = StreamingQualityOptions
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.MeteredQuality,
                        title = StringKey.PREF_STREAMING_QUALITY_METERED_TITLE,
                        options = StreamingQualityOptions
                    )
                )
            )
        )
    )

    val library = SettingsScreen(
        destination = SettingsDestination.Library,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Action(
                        action = SettingsAction.Rescan,
                        title = StringKey.PREF_MEDIA_RESCAN_TITLE,
                        summary = StringKey.PREF_MEDIA_RESCAN_SUMMARY,
                        key = "pref_media_rescan"
                    )
                )
            ),
            SettingsGroup(
                title = StringKey.PREF_CATEGORY_TITLE_ARTWORK,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtworkSettings.LocalOnly,
                        title = StringKey.PREF_ARTWORK_LOCAL_ONLY_TITLE,
                        summary = StringKey.PREF_ARTWORK_LOCAL_ONLY_SUBTITLE
                    )
                )
            )
        )
    )

    override val screens: List<SettingsScreen> = listOf(playbackAndSound, sources, library)
}

@BindingContainer
@ContributesTo(AppScope::class)
object IosSettingsCatalogModule {
    @Provides
    fun provideSettingsCatalog(): SettingsCatalog = IosSettingsCatalog
}
