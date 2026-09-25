package com.simplecityapps.shuttle.ui.screens.settings.model

import android.os.Build
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.downloads.DownloadSettings
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.ThemeMode
import kotlin.math.roundToInt

/**
 * The settings screens, grouped as the settings redesign inventory lays them out: six destinations in place
 * of the nine legacy preference screens. Plain data; the Compose screens render it.
 *
 * Not modelled: the MediaStore/TagLib scanner choice (TagLib is the only scanner; the choice lives in the
 * media provider list, not a preference), and About's version, rate and contact rows, which aren't settings.
 */
object SettingsCatalog {
    val appearance = SettingsScreen(
        destination = SettingsDestination.Appearance,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Choice(
                        setting = AppearanceSettings.Theme,
                        title = R.string.pref_theme_title,
                        options = listOf(
                            ChoiceOption(ThemeMode.DayNight, R.string.theme_entry_day_night),
                            ChoiceOption(ThemeMode.Light, R.string.theme_entry_light),
                            ChoiceOption(ThemeMode.Dark, R.string.theme_entry_dark)
                        )
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.DynamicColour,
                        title = R.string.pref_dynamic_colour_title,
                        summary = R.string.pref_dynamic_colour_summary,
                        minSdk = Build.VERSION_CODES.S
                    ),
                    SettingItem.Choice(
                        setting = AppearanceSettings.AccentColour,
                        title = R.string.pref_theme_accent_title,
                        options = listOf(
                            ChoiceOption(Accent.Default, R.string.theme_accent_entry_blue),
                            ChoiceOption(Accent.Orange, R.string.theme_accent_entry_orange),
                            ChoiceOption(Accent.Cyan, R.string.theme_accent_entry_cyan),
                            ChoiceOption(Accent.Purple, R.string.theme_accent_entry_purple),
                            ChoiceOption(Accent.Green, R.string.theme_accent_entry_green),
                            ChoiceOption(Accent.Amber, R.string.theme_accent_entry_amber)
                        )
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.ColourFromArtwork,
                        title = R.string.pref_colour_from_artwork_title,
                        summary = R.string.pref_colour_from_artwork_summary
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.PureBlack,
                        title = R.string.pref_pure_black_title,
                        summary = R.string.pref_pure_black_summary
                    )
                )
            ),
            SettingsGroup(
                title = R.string.pref_category_title_widgets,
                items = listOf(
                    SettingItem.Slider(
                        setting = AppearanceSettings.WidgetBackgroundOpacity,
                        title = R.string.pref_widget_opacity_title,
                        range = 0f..100f,
                        steps = 0,
                        fromFloat = { it.roundToInt() }
                    )
                )
            ),
            SettingsGroup(
                title = R.string.pref_navigation_title,
                items = listOf(
                    SettingItem.Switch(
                        setting = AppearanceSettings.ShowHomeOnLaunch,
                        title = R.string.pref_show_home_on_launch_title
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
                        title = R.string.pref_disable_shuffle_on_queue_title,
                        summary = R.string.pref_disable_shuffle_on_queue_subtitle
                    ),
                    SettingItem.Switch(
                        setting = PlaybackSettings.UsbDacDirectOutput,
                        title = R.string.pref_bit_perfect_usb_title,
                        summary = R.string.pref_bit_perfect_usb_subtitle,
                        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    )
                )
            ),
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.Equalizer,
                        title = R.string.dsp_equalizer_title
                    ),
                    SettingItem.Choice(
                        setting = PlaybackSettings.ReplayGain,
                        title = R.string.dsp_replay_gain_title,
                        options = listOf(
                            ChoiceOption(ReplayGainMode.Track, R.string.dsp_replay_gain_track),
                            ChoiceOption(ReplayGainMode.Album, R.string.dsp_replay_gain_album),
                            ChoiceOption(ReplayGainMode.Off, R.string.dsp_replay_gain_off)
                        )
                    ),
                    SettingItem.Slider(
                        setting = PlaybackSettings.PreAmpGain,
                        title = R.string.dsp_preamp,
                        range = -ReplayGainAudioProcessor.maxPreAmpGain.toFloat()..ReplayGainAudioProcessor.maxPreAmpGain.toFloat(),
                        steps = 0,
                        fromFloat = { it }
                    )
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
                        title = R.string.pref_report_playback_title,
                        summary = R.string.pref_report_playback_summary
                    ),
                    SettingItem.Switch(
                        setting = DownloadSettings.WifiOnly,
                        title = R.string.pref_download_wifi_only_title,
                        summary = R.string.pref_download_wifi_only_summary
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
                        title = R.string.pref_media_rescan_title,
                        summary = R.string.pref_media_rescan_summary,
                        key = "pref_media_rescan"
                    ),
                    SettingItem.Choice(
                        setting = LibrarySettings.RescanFrequency,
                        title = R.string.pref_rescan_frequency_title,
                        options = listOf(
                            ChoiceOption(ImportFrequency.Never, R.string.pref_rescan_frequency_never),
                            ChoiceOption(ImportFrequency.Daily, R.string.pref_rescan_frequency_daily),
                            ChoiceOption(ImportFrequency.Weekly, R.string.pref_rescan_frequency_weekly)
                        )
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.ExcludedSongs,
                        title = R.string.pref_exclude_title,
                        summary = R.string.pref_exclude_summary,
                        key = "pref_excluded"
                    )
                )
            ),
            SettingsGroup(
                title = R.string.pref_category_title_artwork,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtworkSettings.WifiOnly,
                        title = R.string.pref_artwork_wifi_title,
                        summary = R.string.pref_artwork_wifi_subtitle
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.LocalOnly,
                        title = R.string.pref_artwork_local_only_title,
                        summary = R.string.pref_artwork_local_only_subtitle
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.MediaSessionArtwork,
                        title = R.string.pref_media_session_artwork_title,
                        summary = R.string.pref_media_session_artwork_subtitle
                    ),
                    SettingItem.Action(
                        action = SettingsAction.ClearArtworkCache,
                        title = R.string.pref_clear_artwork_title,
                        summary = R.string.pref_clear_artwork_subtitle,
                        confirmation = Confirmation(
                            title = R.string.settings_dialog_title_clear_artwork,
                            message = R.string.settings_dialog_message_clear_artwork,
                            confirm = R.string.settings_dialog_button_clear_artwork
                        ),
                        key = "pref_clear_artwork"
                    ),
                    SettingItem.Action(
                        action = SettingsAction.DownloadAllArtwork,
                        title = R.string.pref_download_artwork_title,
                        confirmation = Confirmation(
                            title = R.string.settings_dialog_title_download_artwork,
                            message = R.string.settings_dialog_message_download_artwork,
                            confirm = R.string.settings_dialog_button_download_artwork
                        ),
                        key = "pref_download_artwork"
                    )
                )
            )
        )
    )

    /** Remote Config only fetches while analytics is on, so it has no row of its own. */
    val privacy = SettingsScreen(
        destination = SettingsDestination.Privacy,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Switch(
                        setting = PrivacySettings.CrashReporting,
                        title = R.string.pref_crash_reporting_title,
                        summary = R.string.pref_crash_reporting_subtitle
                    ),
                    SettingItem.Switch(
                        setting = PrivacySettings.Analytics,
                        title = R.string.pref_firebase_analytics_title,
                        summary = R.string.pref_firebase_analytics_subtitle
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
                        title = R.string.pref_view_changelog_title,
                        key = "changelog_show"
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.Licences,
                        title = R.string.pref_view_licenses_title,
                        key = "licenses_show"
                    )
                )
            ),
            SettingsGroup(
                title = R.string.settings_group_advanced,
                items = listOf(
                    SettingItem.Switch(
                        setting = DebugSettings.FileLogging,
                        title = R.string.pref_file_logging_title,
                        summary = R.string.pref_file_logging_subtitle
                    ),
                    SettingItem.Action(
                        action = SettingsAction.CopyDebugLogs,
                        title = R.string.pref_copy_debug_logs_subtitle,
                        key = "pref_copy_debug_logs",
                        dependsOn = DebugSettings.FileLogging
                    )
                )
            )
        )
    )

    val screens: List<SettingsScreen> = listOf(appearance, playbackAndSound, sources, library, privacy, about)

    fun screen(destination: SettingsDestination): SettingsScreen = screens.first { it.destination == destination }

    val items: List<SettingItem> get() = screens.flatMap { it.items }

    /**
     * Keys the redesign leaves out, with why. Their stored values stay put.
     */
    val droppedKeys: Map<String, String> = mapOf(
        "changelog_show_on_launch" to "Dropped by decision 12",
        "pref_media_provider" to "Settings > Sources replaces the provider picker, above the catalog's own rows (#379)",
        "pref_library_tabs_all" to "Tab order moves to the Library screen's Edit tabs sheet",
        "pref_library_tabs_enabled" to "Tab visibility moves to the Library screen's Edit tabs sheet"
    )
}
