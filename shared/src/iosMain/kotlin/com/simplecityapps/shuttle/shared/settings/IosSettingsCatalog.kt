package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.ChoiceOption
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsGroup
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
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
 *   theme, which iOS doesn't draw; widget opacity has no widget. Show Home on launch stays: the shell starts on
 *   `ShellViewModel`'s start tab (on by default on iOS, [IosSettingDefaults]).
 * - Playback & sound: USB DAC direct output is Android's mixer. The equalizer and ReplayGain are Android's rows, run by
 *   the S2Playback engine (phase 6, #604). ReplayGain's own pre-amp slider is left out so the one Preamp is the
 *   Equalizer's (#645); streams still carry its stored value (0 dB unless set), through `SongStreamResolver`.
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
            ),
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.Equalizer,
                        title = StringKey.DSP_EQUALIZER_TITLE
                    ),
                    SettingItem.Choice(
                        setting = PlaybackSettings.ReplayGain,
                        title = StringKey.DSP_REPLAY_GAIN_TITLE,
                        options = listOf(
                            ChoiceOption(ReplayGainMode.Track, StringKey.DSP_REPLAY_GAIN_TRACK),
                            ChoiceOption(ReplayGainMode.Album, StringKey.DSP_REPLAY_GAIN_ALBUM),
                            ChoiceOption(ReplayGainMode.Off, StringKey.DSP_REPLAY_GAIN_OFF)
                        )
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

    val appearance = SettingsScreen(
        destination = SettingsDestination.Appearance,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = AppearanceSettings.ShowHomeOnLaunch,
                        title = StringKey.PREF_SHOW_HOME_ON_LAUNCH_TITLE
                    )
                )
            )
        )
    )

    override val screens: List<SettingsScreen> = listOf(playbackAndSound, sources, library, appearance)
}

@BindingContainer
@ContributesTo(AppScope::class)
object IosSettingsCatalogModule {
    @Provides
    fun provideSettingsCatalog(): SettingsCatalog = IosSettingsCatalog
}
