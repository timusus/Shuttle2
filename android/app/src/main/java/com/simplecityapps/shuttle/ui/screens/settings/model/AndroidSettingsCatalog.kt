package com.simplecityapps.shuttle.ui.screens.settings.model

import android.os.Build
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.playback.dsp.replaygain.MAX_REPLAY_GAIN_PREAMP_DB
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.model.PreferredCopy
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtistSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.DownloadSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.NowPlayingImage
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.text.SettingsString
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.math.roundToInt

/**
 * Android's settings screens, grouped as the settings redesign inventory lays them out: six destinations in place
 * of the nine legacy preference screens. Plain data; the Compose screens render it.
 *
 * Not modelled: the MediaStore/TagLib scanner choice (TagLib is the only scanner; the choice lives in the
 * media provider list, not a preference), and About's version, rate and contact rows, which aren't settings.
 */
object AndroidSettingsCatalog : SettingsCatalog {
    val appearance = SettingsScreen(
        destination = SettingsDestination.Appearance,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Choice(
                        setting = AppearanceSettings.Theme,
                        title = SettingsString.PREF_THEME_TITLE,
                        options = listOf(
                            ChoiceOption(ThemeMode.DayNight, SettingsString.THEME_ENTRY_DAY_NIGHT),
                            ChoiceOption(ThemeMode.Light, SettingsString.THEME_ENTRY_LIGHT),
                            ChoiceOption(ThemeMode.Dark, SettingsString.THEME_ENTRY_DARK)
                        )
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.DynamicColour,
                        title = SettingsString.PREF_DYNAMIC_COLOUR_TITLE,
                        summary = SettingsString.PREF_DYNAMIC_COLOUR_SUMMARY,
                        minSdk = Build.VERSION_CODES.S
                    ),
                    SettingItem.Choice(
                        setting = AppearanceSettings.AccentColour,
                        title = SettingsString.PREF_THEME_ACCENT_TITLE,
                        options = listOf(
                            ChoiceOption(Accent.Default, SettingsString.THEME_ACCENT_ENTRY_NEUTRAL),
                            ChoiceOption(Accent.Blue, SettingsString.THEME_ACCENT_ENTRY_BLUE),
                            ChoiceOption(Accent.Orange, SettingsString.THEME_ACCENT_ENTRY_ORANGE),
                            ChoiceOption(Accent.Cyan, SettingsString.THEME_ACCENT_ENTRY_CYAN),
                            ChoiceOption(Accent.Purple, SettingsString.THEME_ACCENT_ENTRY_PURPLE),
                            ChoiceOption(Accent.Green, SettingsString.THEME_ACCENT_ENTRY_GREEN),
                            ChoiceOption(Accent.Amber, SettingsString.THEME_ACCENT_ENTRY_AMBER)
                        ),
                        overriddenBy = SettingOverride(AppearanceSettings.DynamicColour, SettingsString.PREF_THEME_ACCENT_DYNAMIC_COLOUR_HINT)
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.ColourFromArtwork,
                        title = SettingsString.PREF_COLOUR_FROM_ARTWORK_TITLE,
                        summary = SettingsString.PREF_COLOUR_FROM_ARTWORK_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.ClassicalDisplay,
                        title = SettingsString.PREF_CLASSICAL_DISPLAY_TITLE,
                        summary = SettingsString.PREF_CLASSICAL_DISPLAY_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.PureBlack,
                        title = SettingsString.PREF_PURE_BLACK_TITLE,
                        summary = SettingsString.PREF_PURE_BLACK_SUMMARY
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.PREF_CATEGORY_TITLE_WIDGETS,
                items = listOf(
                    SettingItem.Slider(
                        setting = AppearanceSettings.WidgetBackgroundOpacity,
                        title = SettingsString.PREF_WIDGET_OPACITY_TITLE,
                        range = 0f..100f,
                        steps = 0,
                        fromFloat = { it.roundToInt() },
                        label = SettingItem.SliderLabel.Percent
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.PREF_NAVIGATION_TITLE,
                items = listOf(
                    SettingItem.Switch(
                        setting = AppearanceSettings.ShowHomeOnLaunch,
                        title = SettingsString.PREF_SHOW_HOME_ON_LAUNCH_TITLE
                    )
                )
            )
        )
    )

    val playbackAndSound = SettingsScreen(
        destination = SettingsDestination.PlaybackAndSound,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = PlaybackSettings.RetainShuffleOnNewQueue,
                        title = SettingsString.PREF_DISABLE_SHUFFLE_ON_QUEUE_TITLE,
                        summary = SettingsString.PREF_DISABLE_SHUFFLE_ON_QUEUE_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = PlaybackSettings.UsbDacDirectOutput,
                        title = SettingsString.PREF_BIT_PERFECT_USB_TITLE,
                        summary = SettingsString.PREF_BIT_PERFECT_USB_SUBTITLE,
                        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ),
                    SettingItem.Switch(
                        setting = PlaybackSettings.FetchLyricsOnline,
                        title = SettingsString.PREF_FETCH_LYRICS_ONLINE_TITLE,
                        summary = SettingsString.PREF_FETCH_LYRICS_ONLINE_SUBTITLE
                    )
                )
            ),
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.Equalizer,
                        title = SettingsString.DSP_EQUALIZER_TITLE,
                        stateSetting = EqualizerSettings.Enabled
                    ),
                    SettingItem.Choice(
                        setting = PlaybackSettings.ReplayGain,
                        title = SettingsString.DSP_REPLAY_GAIN_TITLE,
                        options = listOf(
                            // Turning ReplayGain on is Shuttle Music Pro; turning it off stays free
                            ChoiceOption(ReplayGainMode.Track, SettingsString.DSP_REPLAY_GAIN_TRACK, ProFeature.AdvancedAudio),
                            ChoiceOption(ReplayGainMode.Album, SettingsString.DSP_REPLAY_GAIN_ALBUM, ProFeature.AdvancedAudio),
                            ChoiceOption(ReplayGainMode.Off, SettingsString.DSP_REPLAY_GAIN_OFF)
                        )
                    ),
                    SettingItem.Slider(
                        setting = PlaybackSettings.PreAmpGain,
                        title = SettingsString.DSP_PREAMP,
                        range = -MAX_REPLAY_GAIN_PREAMP_DB.toFloat()..MAX_REPLAY_GAIN_PREAMP_DB.toFloat(),
                        steps = 0,
                        fromFloat = { it },
                        label = SettingItem.SliderLabel.DecibelsSigned
                    ),
                    SettingItem.Slider(
                        setting = PlaybackSettings.CrossfadeDuration,
                        title = SettingsString.PREF_CROSSFADE_TITLE,
                        range = 0f..12_000f,
                        steps = 11,
                        fromFloat = { it.roundToInt() },
                        label = SettingItem.SliderLabel.CrossfadeSeconds
                    )
                )
            )
        ) + SettingsGroup(
            // Hidden in a build without a Last.fm API key and secret (SettingsUiState.lastFmConfigured): see SettingsScreen.withoutScrobbling.
            title = null,
            items = listOf(
                SettingItem.Navigate(
                    target = SettingsLink.Scrobbling,
                    title = SettingsString.SETTINGS_SCROBBLING_TITLE
                )
            )
        )
    )

    val sources = SettingsScreen(
        destination = SettingsDestination.Sources,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = LibrarySettings.ReportPlaybackToServer,
                        title = SettingsString.PREF_REPORT_PLAYBACK_TITLE,
                        summary = SettingsString.PREF_REPORT_PLAYBACK_SUMMARY
                    )
                )
            ),
            // Every network rule in one place: what streams at on each network, the format a transcode is in, what
            // downloads keep (whatever the network: a download is kept, so it has its own quality), and what only runs
            // on Wi-Fi.
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_STREAMING_AND_DOWNLOADS,
                items = listOf(
                    SettingItem.Choice(
                        setting = StreamingSettings.UnmeteredQuality,
                        title = SettingsString.PREF_STREAMING_QUALITY_UNMETERED_TITLE,
                        options = StreamingQualityOptions
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.MeteredQuality,
                        title = SettingsString.PREF_STREAMING_QUALITY_METERED_TITLE,
                        options = StreamingQualityOptions
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.Format,
                        title = SettingsString.PREF_TRANSCODE_FORMAT_TITLE,
                        options = TranscodeFormatOptions,
                        summary = SettingsString.PREF_TRANSCODE_FORMAT_SUMMARY
                    ),
                    SettingItem.Choice(
                        setting = StreamingSettings.DownloadQuality,
                        title = SettingsString.PREF_DOWNLOAD_QUALITY_TITLE,
                        options = StreamingQualityOptions
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.Downloads,
                        title = SettingsString.PREF_DOWNLOADS_TITLE,
                        summary = SettingsString.PREF_DOWNLOADS_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = DownloadSettings.OfflineMode,
                        title = SettingsString.PREF_OFFLINE_MODE_TITLE,
                        summary = SettingsString.PREF_OFFLINE_MODE_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = DownloadSettings.WifiOnly,
                        title = SettingsString.PREF_DOWNLOAD_WIFI_ONLY_TITLE,
                        summary = SettingsString.PREF_DOWNLOAD_WIFI_ONLY_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.WifiOnly,
                        title = SettingsString.PREF_ARTWORK_WIFI_TITLE,
                        summary = SettingsString.PREF_ARTWORK_WIFI_SUBTITLE
                    )
                )
            )
        )
    )

    val library = SettingsScreen(
        destination = SettingsDestination.Library,
        groups = listOf(
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_SCANNING,
                items = listOf(
                    // The manual rescan lives on Sources' This device card, next to the scan's progress.
                    SettingItem.Choice(
                        setting = LibrarySettings.RescanFrequency,
                        title = SettingsString.PREF_RESCAN_FREQUENCY_TITLE,
                        options = listOf(
                            ChoiceOption(ImportFrequency.Never, SettingsString.PREF_RESCAN_FREQUENCY_NEVER),
                            ChoiceOption(ImportFrequency.Daily, SettingsString.PREF_RESCAN_FREQUENCY_DAILY),
                            ChoiceOption(ImportFrequency.Weekly, SettingsString.PREF_RESCAN_FREQUENCY_WEEKLY)
                        ),
                        showsLastScan = true
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.ExcludedSongs,
                        title = SettingsString.PREF_EXCLUDE_TITLE,
                        summary = SettingsString.PREF_EXCLUDE_SUMMARY,
                        key = "pref_excluded"
                    ),
                    SettingItem.Choice(
                        setting = LibrarySettings.MinTrackLength,
                        title = SettingsString.PREF_MIN_TRACK_LENGTH_TITLE,
                        summary = SettingsString.PREF_MIN_TRACK_LENGTH_SUMMARY,
                        options = listOf(
                            ChoiceOption(MinTrackLength.Off, SettingsString.PREF_MIN_TRACK_LENGTH_OFF),
                            ChoiceOption(MinTrackLength.TenSeconds, SettingsString.PREF_MIN_TRACK_LENGTH_10_SECONDS),
                            ChoiceOption(MinTrackLength.ThirtySeconds, SettingsString.PREF_MIN_TRACK_LENGTH_30_SECONDS),
                            ChoiceOption(MinTrackLength.SixtySeconds, SettingsString.PREF_MIN_TRACK_LENGTH_60_SECONDS)
                        )
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_COPIES,
                items = listOf(
                    SettingItem.Switch(
                        setting = LibrarySettings.MergeCopies,
                        title = SettingsString.PREF_MERGE_COPIES_TITLE,
                        summary = SettingsString.PREF_MERGE_COPIES_SUMMARY
                    ),
                    SettingItem.Choice(
                        setting = LibrarySettings.PreferredCopy,
                        title = SettingsString.PREF_PREFERRED_COPY_TITLE,
                        options = listOf(
                            ChoiceOption(PreferredCopy.Local, SettingsString.PREF_PREFERRED_COPY_LOCAL, description = SettingsString.PREF_PREFERRED_COPY_LOCAL_DESCRIPTION),
                            ChoiceOption(PreferredCopy.BestQuality, SettingsString.PREF_PREFERRED_COPY_BEST_QUALITY, description = SettingsString.PREF_PREFERRED_COPY_BEST_QUALITY_DESCRIPTION)
                        ),
                        dependsOn = LibrarySettings.MergeCopies,
                        disabledHint = SettingsString.PREF_PREFERRED_COPY_MERGING_OFF
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_ARTISTS,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtistSettings.ShowCreditedArtists,
                        title = SettingsString.PREF_SHOW_CREDITED_ARTISTS_TITLE,
                        summary = SettingsString.PREF_SHOW_CREDITED_ARTISTS_SUMMARY
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.PREF_CATEGORY_TITLE_BACKUP,
                items = listOf(
                    SettingItem.Action(
                        action = SettingsAction.ExportBackup,
                        title = SettingsString.PREF_BACKUP_EXPORT_TITLE,
                        summary = SettingsString.PREF_BACKUP_EXPORT_SUMMARY,
                        key = "pref_backup_export"
                    ),
                    SettingItem.Action(
                        action = SettingsAction.ImportBackup,
                        title = SettingsString.PREF_BACKUP_IMPORT_TITLE,
                        summary = SettingsString.PREF_BACKUP_IMPORT_SUMMARY,
                        confirmation = Confirmation(
                            title = SettingsString.SETTINGS_DIALOG_TITLE_RESTORE_BACKUP,
                            message = SettingsString.SETTINGS_DIALOG_MESSAGE_RESTORE_BACKUP,
                            confirm = SettingsString.SETTINGS_DIALOG_BUTTON_RESTORE_BACKUP
                        ),
                        key = "pref_backup_import"
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.PREF_CATEGORY_TITLE_ARTWORK,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtworkSettings.LocalOnly,
                        title = SettingsString.PREF_ARTWORK_LOCAL_ONLY_TITLE,
                        summary = SettingsString.PREF_ARTWORK_LOCAL_ONLY_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.MediaSessionArtwork,
                        title = SettingsString.PREF_MEDIA_SESSION_ARTWORK_TITLE,
                        summary = SettingsString.PREF_MEDIA_SESSION_ARTWORK_SUBTITLE
                    ),
                    SettingItem.Choice(
                        setting = ArtworkSettings.NowPlayingArtworkSource,
                        title = SettingsString.PREF_NOW_PLAYING_ARTWORK_TITLE,
                        summary = SettingsString.PREF_NOW_PLAYING_ARTWORK_SUBTITLE,
                        options = listOf(
                            ChoiceOption(NowPlayingImage.AlbumArt, SettingsString.NOW_PLAYING_ARTWORK_ENTRY_ALBUM_ART),
                            ChoiceOption(NowPlayingImage.ArtistImage, SettingsString.NOW_PLAYING_ARTWORK_ENTRY_ARTIST_IMAGE)
                        )
                    ),
                    SettingItem.Action(
                        action = SettingsAction.ClearArtworkCache,
                        title = SettingsString.PREF_CLEAR_ARTWORK_TITLE,
                        summary = SettingsString.PREF_CLEAR_ARTWORK_SUBTITLE,
                        confirmation = Confirmation(
                            title = SettingsString.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK,
                            message = SettingsString.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK,
                            confirm = SettingsString.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK
                        ),
                        key = "pref_clear_artwork"
                    ),
                    SettingItem.Action(
                        action = SettingsAction.DownloadAllArtwork,
                        title = SettingsString.PREF_DOWNLOAD_ARTWORK_TITLE,
                        confirmation = Confirmation(
                            title = SettingsString.SETTINGS_DIALOG_TITLE_DOWNLOAD_ARTWORK,
                            message = SettingsString.SETTINGS_DIALOG_MESSAGE_DOWNLOAD_ARTWORK,
                            confirm = SettingsString.SETTINGS_DIALOG_BUTTON_DOWNLOAD_ARTWORK
                        ),
                        key = "pref_download_artwork"
                    )
                )
            )
        )
    )

    val privacy = SettingsScreen(
        destination = SettingsDestination.Privacy,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = PrivacySettings.CrashReporting,
                        title = SettingsString.PREF_CRASH_REPORTING_TITLE,
                        summary = SettingsString.PREF_CRASH_REPORTING_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = PrivacySettings.Analytics,
                        title = SettingsString.PREF_ANALYTICS_TITLE,
                        summary = SettingsString.PREF_ANALYTICS_SUBTITLE
                    )
                )
            )
        )
    )

    val about = SettingsScreen(
        destination = SettingsDestination.About,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.WhatsNew,
                        title = SettingsString.PREF_VIEW_CHANGELOG_TITLE,
                        key = "changelog_show"
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.Licences,
                        title = SettingsString.PREF_VIEW_LICENSES_TITLE,
                        key = "licenses_show"
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_MORE_FROM_SIMPLECITY,
                items = listOf(
                    SettingItem.Action(
                        action = SettingsAction.OpenShuttlePodcasts,
                        title = SettingsString.PREF_SHUTTLE_PODCASTS_TITLE,
                        summary = SettingsString.PREF_SHUTTLE_PODCASTS_SUMMARY,
                        key = "pref_shuttle_podcasts"
                    )
                )
            ),
            SettingsGroup(
                title = SettingsString.SETTINGS_GROUP_ADVANCED,
                items = listOfNotNull(
                    SettingItem.Switch(
                        setting = DebugSettings.FileLogging,
                        title = SettingsString.PREF_FILE_LOGGING_TITLE,
                        summary = SettingsString.PREF_FILE_LOGGING_SUBTITLE
                    ),
                    SettingItem.Action(
                        action = SettingsAction.ShareDebugLogs,
                        title = SettingsString.PREF_SHARE_DEBUG_LOGS_TITLE,
                        key = "pref_share_debug_logs",
                        dependsOn = DebugSettings.FileLogging
                    ),
                    // Debug builds only: the row costs release nothing but this check; the screen lives in src/debug.
                    SettingItem.Navigate(
                        target = SettingsLink.LiveLog,
                        title = SettingsString.PREF_VIEW_LIVE_LOG_TITLE,
                        key = "pref_view_live_log"
                    ).takeIf { BuildConfig.DEBUG }
                )
            )
        )
    )

    override val screens: List<SettingsScreen> = listOf(appearance, playbackAndSound, sources, library, privacy, about)

    /**
     * Keys the redesign leaves out, with why. Their stored values stay put.
     */
    val droppedKeys: Map<String, String> = mapOf(
        "changelog_show_on_launch" to "Dropped by decision 12",
        "pref_media_rescan" to "The manual rescan lives on Settings > Sources' This device card only",
        "pref_media_provider" to "Settings > Sources replaces the provider picker, above the catalog's own rows (#379)",
        "pref_library_tabs_all" to "Tab order moves to the Library screen's Edit tabs sheet",
        "pref_library_tabs_enabled" to "Tab visibility moves to the Library screen's Edit tabs sheet"
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object SettingsCatalogModule {
    @Provides
    fun provideSettingsCatalog(): SettingsCatalog = AndroidSettingsCatalog
}
