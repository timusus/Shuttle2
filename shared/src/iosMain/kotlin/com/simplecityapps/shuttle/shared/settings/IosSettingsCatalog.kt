package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.playback.dsp.replaygain.MAX_REPLAY_GAIN_PREAMP_DB
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtistSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DownloadSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
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
import com.simplecityapps.shuttle.ui.screens.settings.model.TranscodeFormatOptions
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
 * - Appearance: theme, dynamic colour, accent and pure black restyle Android's Material theme, which iOS doesn't
 *   draw; widget opacity has no widget. Colour from artwork stays: `ArtworkTintModifier` falls back to the accent
 *   when it's off. Show Home on launch stays: the shell starts on
 *   `ShellViewModel`'s start tab (on by default on iOS, [IosSettingDefaults]).
 * - Playback & sound: USB DAC direct output is Android's mixer. The equalizer, ReplayGain and its pre-amp are Android's
 *   rows, run by the S2Playback engine (phase 6, #604); ReplayGain and its pre-amp are a group of their own, apart
 *   from the Equalizer's Preamp (#645).
 * - Sources: reporting playback to the server has no iOS reporter yet; download on Wi-Fi only is
 *   Android's download requirements (`DownloadSettings`); artwork on Wi-Fi only, below.
 * - Library: rescan frequency needs a background scheduler; excluded songs and folders wait for local files (phase
 *   8); artwork Wi-Fi only gates the S2 artwork service iOS doesn't use; media session artwork, clearing the
 *   artwork cache and downloading all artwork reach Android's Coil and media session.
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
                        title = StringKey.DSP_EQUALIZER_TITLE,
                        stateSetting = EqualizerSettings.Enabled
                    )
                )
            ),
            // ReplayGain and its own pre-amp, a group apart from the Equalizer's Preamp (#645).
            SettingsGroup(
                title = StringKey.DSP_REPLAY_GAIN_TITLE,
                items = listOf(
                    SettingItem.Choice(
                        setting = PlaybackSettings.ReplayGain,
                        title = StringKey.DSP_REPLAY_GAIN_TITLE,
                        options = listOf(
                            // Turning ReplayGain on is Shuttle Music Pro, as on Android (#946); turning it off stays free
                            ChoiceOption(ReplayGainMode.Track, StringKey.DSP_REPLAY_GAIN_TRACK, ProFeature.AdvancedAudio),
                            ChoiceOption(ReplayGainMode.Album, StringKey.DSP_REPLAY_GAIN_ALBUM, ProFeature.AdvancedAudio),
                            ChoiceOption(ReplayGainMode.Off, StringKey.DSP_REPLAY_GAIN_OFF)
                        )
                    ),
                    SettingItem.Slider(
                        setting = PlaybackSettings.PreAmpGain,
                        title = StringKey.DSP_PREAMP,
                        range = -MAX_REPLAY_GAIN_PREAMP_DB.toFloat()..MAX_REPLAY_GAIN_PREAMP_DB.toFloat(),
                        steps = 0,
                        fromFloat = { it }
                    )
                )
            ),
            // Hidden in a build without a Last.fm API key and secret (SettingsUiState.lastFmConfigured): SettingsView
            // applies SettingsScreen.withoutScrobbling.
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.Scrobbling,
                        title = StringKey.SETTINGS_SCROBBLING_TITLE
                    )
                )
            )
        )
    )

    val sources = SettingsScreen(
        destination = SettingsDestination.Sources,
        groups = listOf(
            SettingsGroup(
                title = StringKey.SETTINGS_GROUP_STREAMING_AND_DOWNLOADS,
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
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.Format,
                        title = StringKey.PREF_TRANSCODE_FORMAT_TITLE,
                        options = TranscodeFormatOptions,
                        summary = StringKey.PREF_TRANSCODE_FORMAT_SUMMARY
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.DownloadQuality,
                        title = StringKey.PREF_DOWNLOAD_QUALITY_TITLE,
                        options = StreamingQualityOptions
                    ),
                    SettingItem.Switch(
                        setting = DownloadSettings.WifiOnly,
                        title = StringKey.PREF_DOWNLOAD_WIFI_ONLY_TITLE,
                        summary = StringKey.PREF_DOWNLOAD_WIFI_ONLY_SUMMARY
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
            // Read by the shared AlbumArtistListViewModel behind Library > Artists (#637)
            SettingsGroup(
                title = StringKey.SETTINGS_GROUP_ARTISTS,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtistSettings.ShowCreditedArtists,
                        title = StringKey.PREF_SHOW_CREDITED_ARTISTS_TITLE,
                        summary = StringKey.PREF_SHOW_CREDITED_ARTISTS_SUMMARY
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
                        setting = AppearanceSettings.ColourFromArtwork,
                        title = StringKey.PREF_COLOUR_FROM_ARTWORK_TITLE,
                        summary = StringKey.PREF_COLOUR_FROM_ARTWORK_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.ShowHomeOnLaunch,
                        title = StringKey.PREF_SHOW_HOME_ON_LAUNCH_TITLE
                    )
                )
            )
        )
    )

    /** Crash reporting (Sentry) and analytics (PostHog), each started and stopped by the shared consent gate. */
    val privacy = SettingsScreen(
        destination = SettingsDestination.Privacy,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = PrivacySettings.CrashReporting,
                        title = StringKey.PREF_CRASH_REPORTING_TITLE,
                        summary = StringKey.PREF_CRASH_REPORTING_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = PrivacySettings.Analytics,
                        title = StringKey.PREF_ANALYTICS_TITLE,
                        summary = StringKey.PREF_ANALYTICS_SUBTITLE
                    )
                )
            )
        )
    )

    override val screens: List<SettingsScreen> = listOf(playbackAndSound, sources, library, appearance, privacy)
}

@BindingContainer
@ContributesTo(AppScope::class)
object IosSettingsCatalogModule {
    @Provides
    fun provideSettingsCatalog(): SettingsCatalog = IosSettingsCatalog
}
