package com.simplecityapps.shuttle.ui.screens.settings.model

import android.os.Build
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.downloads.DownloadSettings
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.text.StringKey
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
                        title = StringKey.PREF_THEME_TITLE,
                        options = listOf(
                            ChoiceOption(ThemeMode.DayNight, StringKey.THEME_ENTRY_DAY_NIGHT),
                            ChoiceOption(ThemeMode.Light, StringKey.THEME_ENTRY_LIGHT),
                            ChoiceOption(ThemeMode.Dark, StringKey.THEME_ENTRY_DARK)
                        )
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.DynamicColour,
                        title = StringKey.PREF_DYNAMIC_COLOUR_TITLE,
                        summary = StringKey.PREF_DYNAMIC_COLOUR_SUMMARY,
                        minSdk = Build.VERSION_CODES.S
                    ),
                    SettingItem.Choice(
                        setting = AppearanceSettings.AccentColour,
                        title = StringKey.PREF_THEME_ACCENT_TITLE,
                        options = listOf(
                            ChoiceOption(Accent.Default, StringKey.THEME_ACCENT_ENTRY_BLUE),
                            ChoiceOption(Accent.Orange, StringKey.THEME_ACCENT_ENTRY_ORANGE),
                            ChoiceOption(Accent.Cyan, StringKey.THEME_ACCENT_ENTRY_CYAN),
                            ChoiceOption(Accent.Purple, StringKey.THEME_ACCENT_ENTRY_PURPLE),
                            ChoiceOption(Accent.Green, StringKey.THEME_ACCENT_ENTRY_GREEN),
                            ChoiceOption(Accent.Amber, StringKey.THEME_ACCENT_ENTRY_AMBER)
                        ),
                        overriddenBy = SettingOverride(AppearanceSettings.DynamicColour, StringKey.PREF_THEME_ACCENT_DYNAMIC_COLOUR_HINT)
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.ColourFromArtwork,
                        title = StringKey.PREF_COLOUR_FROM_ARTWORK_TITLE,
                        summary = StringKey.PREF_COLOUR_FROM_ARTWORK_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = AppearanceSettings.PureBlack,
                        title = StringKey.PREF_PURE_BLACK_TITLE,
                        summary = StringKey.PREF_PURE_BLACK_SUMMARY
                    )
                )
            ),
            SettingsGroup(
                title = StringKey.PREF_CATEGORY_TITLE_WIDGETS,
                items = listOf(
                    SettingItem.Slider(
                        setting = AppearanceSettings.WidgetBackgroundOpacity,
                        title = StringKey.PREF_WIDGET_OPACITY_TITLE,
                        range = 0f..100f,
                        steps = 0,
                        fromFloat = { it.roundToInt() }
                    )
                )
            ),
            SettingsGroup(
                title = StringKey.PREF_NAVIGATION_TITLE,
                items = listOf(
                    SettingItem.Switch(
                        setting = AppearanceSettings.ShowHomeOnLaunch,
                        title = StringKey.PREF_SHOW_HOME_ON_LAUNCH_TITLE
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
                        title = StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_TITLE,
                        summary = StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = PlaybackSettings.UsbDacDirectOutput,
                        title = StringKey.PREF_BIT_PERFECT_USB_TITLE,
                        summary = StringKey.PREF_BIT_PERFECT_USB_SUBTITLE,
                        minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
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
                    ),
                    SettingItem.Slider(
                        setting = PlaybackSettings.PreAmpGain,
                        title = StringKey.DSP_PREAMP,
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
                        title = StringKey.PREF_REPORT_PLAYBACK_TITLE,
                        summary = StringKey.PREF_REPORT_PLAYBACK_SUMMARY
                    ),
                    SettingItem.Switch(
                        setting = DownloadSettings.WifiOnly,
                        title = StringKey.PREF_DOWNLOAD_WIFI_ONLY_TITLE,
                        summary = StringKey.PREF_DOWNLOAD_WIFI_ONLY_SUMMARY
                    )
                )
            ),
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
                    ),
                    SettingItem.Choice(
                        setting = LibrarySettings.RescanFrequency,
                        title = StringKey.PREF_RESCAN_FREQUENCY_TITLE,
                        options = listOf(
                            ChoiceOption(ImportFrequency.Never, StringKey.PREF_RESCAN_FREQUENCY_NEVER),
                            ChoiceOption(ImportFrequency.Daily, StringKey.PREF_RESCAN_FREQUENCY_DAILY),
                            ChoiceOption(ImportFrequency.Weekly, StringKey.PREF_RESCAN_FREQUENCY_WEEKLY)
                        )
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.ExcludedSongs,
                        title = StringKey.PREF_EXCLUDE_TITLE,
                        summary = StringKey.PREF_EXCLUDE_SUMMARY,
                        key = "pref_excluded"
                    )
                )
            ),
            SettingsGroup(
                title = StringKey.PREF_CATEGORY_TITLE_ARTWORK,
                items = listOf(
                    SettingItem.Switch(
                        setting = ArtworkSettings.WifiOnly,
                        title = StringKey.PREF_ARTWORK_WIFI_TITLE,
                        summary = StringKey.PREF_ARTWORK_WIFI_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.LocalOnly,
                        title = StringKey.PREF_ARTWORK_LOCAL_ONLY_TITLE,
                        summary = StringKey.PREF_ARTWORK_LOCAL_ONLY_SUBTITLE
                    ),
                    SettingItem.Switch(
                        setting = ArtworkSettings.MediaSessionArtwork,
                        title = StringKey.PREF_MEDIA_SESSION_ARTWORK_TITLE,
                        summary = StringKey.PREF_MEDIA_SESSION_ARTWORK_SUBTITLE
                    ),
                    SettingItem.Action(
                        action = SettingsAction.ClearArtworkCache,
                        title = StringKey.PREF_CLEAR_ARTWORK_TITLE,
                        summary = StringKey.PREF_CLEAR_ARTWORK_SUBTITLE,
                        confirmation = Confirmation(
                            title = StringKey.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK,
                            message = StringKey.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK,
                            confirm = StringKey.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK
                        ),
                        key = "pref_clear_artwork"
                    ),
                    SettingItem.Action(
                        action = SettingsAction.DownloadAllArtwork,
                        title = StringKey.PREF_DOWNLOAD_ARTWORK_TITLE,
                        confirmation = Confirmation(
                            title = StringKey.SETTINGS_DIALOG_TITLE_DOWNLOAD_ARTWORK,
                            message = StringKey.SETTINGS_DIALOG_MESSAGE_DOWNLOAD_ARTWORK,
                            confirm = StringKey.SETTINGS_DIALOG_BUTTON_DOWNLOAD_ARTWORK
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

    val about = SettingsScreen(
        destination = SettingsDestination.About,
        groups = listOf(
            SettingsGroup(
                title = null,
                items = listOf(
                    SettingItem.Navigate(
                        target = SettingsLink.WhatsNew,
                        title = StringKey.PREF_VIEW_CHANGELOG_TITLE,
                        key = "changelog_show"
                    ),
                    SettingItem.Navigate(
                        target = SettingsLink.Licences,
                        title = StringKey.PREF_VIEW_LICENSES_TITLE,
                        key = "licenses_show"
                    )
                )
            ),
            SettingsGroup(
                title = StringKey.SETTINGS_GROUP_ADVANCED,
                items = listOfNotNull(
                    SettingItem.Switch(
                        setting = DebugSettings.FileLogging,
                        title = StringKey.PREF_FILE_LOGGING_TITLE,
                        summary = StringKey.PREF_FILE_LOGGING_SUBTITLE
                    ),
                    SettingItem.Action(
                        action = SettingsAction.CopyDebugLogs,
                        title = StringKey.PREF_COPY_DEBUG_LOGS_SUBTITLE,
                        key = "pref_copy_debug_logs",
                        dependsOn = DebugSettings.FileLogging
                    ),
                    // Debug builds only: the live view of DebugLoggingTree's output (#433). The row itself
                    // costs release nothing but this check; the screen and its buffer live in src/debug.
                    SettingItem.Navigate(
                        target = SettingsLink.LiveLog,
                        title = StringKey.PREF_VIEW_LIVE_LOG_TITLE,
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
