package com.simplecityapps.shuttle.settings

import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.downloads.DownloadSettings
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Every [Setting]'s key and default, pinned: a saved value is found by its key, so renaming one silently resets it for
 * everyone who changed it. The list is the settings as they were before the KeyValueStore move (#584); a new
 * setting adds a line here, and a changed key or default needs a migration, not an edit to this list.
 */
class SettingKeysTest {
    private val settingsClasses = listOf(
        AnalyticsConsentSettings::class,
        AppearanceSettings::class,
        ArtworkSettings::class,
        DebugSettings::class,
        PrivacySettings::class,
        StreamingSettings::class,
        SourcesSettings::class,
        LibrarySettings::class,
        PlaybackSettings::class,
        DownloadSettings::class
    )

    @Test
    fun `every setting keeps its key and default`() {
        // The settings are companion vals, which compile to static fields on the settings class
        val settings = settingsClasses.flatMap { settingsClass ->
            settingsClass.java.declaredFields
                .filter { field -> Setting::class.java.isAssignableFrom(field.type) }
                .map { field ->
                    field.isAccessible = true
                    val setting = field.get(null) as Setting<*>
                    "${settingsClass.simpleName}.${field.name}: ${setting.key} = ${setting.default}"
                }
        }

        settings shouldBe listOf(
            "AnalyticsConsentSettings.Asked: pref_analytics_consent_asked = false",
            "AnalyticsConsentSettings.NoticeShown: pref_analytics_notice_shown = false",
            "AppearanceSettings.Theme: pref_theme = DayNight",
            "AppearanceSettings.AccentColour: pref_theme_accent = Default",
            "AppearanceSettings.PureBlack: pref_theme_extra_dark = false",
            "AppearanceSettings.DynamicColour: pref_theme_dynamic_colour = true",
            "AppearanceSettings.ColourFromArtwork: pref_theme_colour_from_artwork = true",
            "AppearanceSettings.ShowHomeOnLaunch: pref_show_home_on_launch = false",
            "AppearanceSettings.WidgetBackgroundOpacity: widget_background_opacity = 100",
            "AppearanceSettings.CompactMode: pref_compact_mode = false",
            "ArtworkSettings.WifiOnly: artwork_wifi_only = true",
            "ArtworkSettings.LocalOnly: artwork_local_only = false",
            "ArtworkSettings.MediaSessionArtwork: media_session_artwork = true",
            "DebugSettings.FileLogging: pref_file_logging = false",
            "PrivacySettings.CrashReporting: pref_crash_reporting = true",
            "PrivacySettings.Analytics: pref_firebase_analytics = true",
            "StreamingSettings.UnmeteredQuality: pref_streaming_quality_unmetered = Original",
            "StreamingSettings.MeteredQuality: pref_streaming_quality_metered = Original",
            "SourcesSettings.MusicPermissionRequested: music_permission_requested = false",
            "SourcesSettings.ExcludedFolders: scanner_excluded_folders = []",
            "SourcesSettings.ExtraFolders: scanner_extra_folders = []",
            "SourcesSettings.IncludedFolders: scanner_included_folders = []",
            "SourcesSettings.IncludedFoldersMigrated: scanner_included_folders_migrated = false",
            "LibrarySettings.RescanFrequency: pref_media_rescan_frequency = Never",
            "LibrarySettings.ReportPlaybackToServer: pref_report_playback = true",
            "PlaybackSettings.RetainShuffleOnNewQueue: pref_retain_shuffle_on_new_queue = false",
            "PlaybackSettings.UsbDacDirectOutput: pref_bit_perfect_usb = false",
            "PlaybackSettings.EqualizerEnabled: equalizer_enabled = false",
            "PlaybackSettings.EqualizerPreampGain: equalizer_preamp_gain = 0.0",
            "PlaybackSettings.ReplayGain: replaygain_mode = Off",
            "PlaybackSettings.PreAmpGain: preamp_gain = 0.0",
            "PlaybackSettings.PlaybackSpeed: playback_speed = 1.0",
            "PlaybackSettings.CrossfadeDuration: crossfade_duration_ms = 0",
            "DownloadSettings.WifiOnly: pref_download_wifi_only = true"
        )
    }
}
