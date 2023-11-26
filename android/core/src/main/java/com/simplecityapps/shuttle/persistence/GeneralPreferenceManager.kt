package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences
import java.util.Date

class GeneralPreferenceManager(private val sharedPreferences: SharedPreferences) {
    var previousVersionCode: Int
        set(value) {
            sharedPreferences.put("previous_version_code", value)
        }
        get() {
            return sharedPreferences.get("previous_version_code", -1)
        }

    var showChangelogOnLaunch: Boolean
        set(value) {
            sharedPreferences.put("changelog_show_on_launch", value)
        }
        get() {
            return sharedPreferences.get("changelog_show_on_launch", true)
        }

    var lastViewedChangelogVersion: String?
        set(value) {
            sharedPreferences.put("last_viewed_changelog_version", value)
        }
        get() {
            return sharedPreferences.getString("last_viewed_changelog_version", null)
        }

    var lastViewedTrialDialogDate: Date?
        set(value) {
            sharedPreferences.put("last_viewed_trial_dialog", value?.time)
        }
        get() {
            val time = sharedPreferences.getLong("last_viewed_trial_dialog", -1)
            if (time != -1L) {
                return Date(time)
            }
            return null
        }

    var appPurchasedDate: Date?
        set(value) {
            sharedPreferences.put("app_purchased_date", value?.time)
        }
        get() {
            val time = sharedPreferences.getLong("app_purchased_date", -1)
            if (time != -1L) {
                return Date(time)
            }
            return null
        }

    var lastViewedRatingFlow: Date?
        set(value) {
            sharedPreferences.put("last_viewed_rating_flow", value?.time)
        }
        get() {
            val time = sharedPreferences.getLong("last_viewed_rating_flow", -1)
            if (time != -1L) {
                return Date(time)
            }
            return null
        }

    var hasSeenThankYouDialog: Boolean
        set(value) {
            sharedPreferences.put("thank_you_dialog_viewed", value)
        }
        get() {
            return sharedPreferences.get("thank_you_dialog_viewed", false)
        }

    enum class Theme {
        DayNight,
        Light,
        Dark
    }

    var themeBase: Theme
        set(value) {
            sharedPreferences.put("pref_theme", value.ordinal.toString())
        }
        get() {
            return Theme.values()[sharedPreferences.get("pref_theme", "0").toInt()]
        }

    enum class Accent {
        Default,
        Orange,
        Cyan,
        Purple,
        Green,
        Amber
    }

    var themeAccent: Accent
        set(value) {
            sharedPreferences.put("pref_theme_accent", value.ordinal.toString())
        }
        get() {
            return Accent.values()[sharedPreferences.get("pref_theme_accent", "0").toInt()]
        }

    var themeExtraDark: Boolean
        set(value) {
            sharedPreferences.put("pref_theme_extra_dark", value)
        }
        get() {
            return sharedPreferences.get("pref_theme_extra_dark", false)
        }

    var artworkWifiOnly: Boolean
        set(value) {
            sharedPreferences.put("artwork_wifi_only", value)
        }
        get() {
            return sharedPreferences.get("artwork_wifi_only", true)
        }

    var artworkLocalOnly: Boolean
        set(value) {
            sharedPreferences.put("artwork_local_only", value)
        }
        get() {
            return sharedPreferences.get("artwork_local_only", false)
        }

    var crashReportingEnabled: Boolean
        set(value) {
            sharedPreferences.put("pref_crash_reporting", value)
        }
        get() {
            return sharedPreferences.get("pref_crash_reporting", false)
        }

    var firebaseAnalyticsEnabled: Boolean
        set(value) {
            sharedPreferences.put("pref_firebase_analytics", value)
        }
        get() {
            return sharedPreferences.get("pref_firebase_analytics", false)
        }

    var artistListViewMode: String?
        set(value) {
            sharedPreferences.put("pref_artist_view_mode", value)
        }
        get() {
            return sharedPreferences.getString("pref_artist_view_mode", null)
        }

    var albumListViewMode: String?
        set(value) {
            sharedPreferences.put("pref_album_view_mode", value)
        }
        get() {
            return sharedPreferences.getString("pref_album_view_mode", null)
        }

    var hasSeenOnboardingAnalyticsDialog: Boolean
        set(value) {
            sharedPreferences.put("onboarding_analytics_dialog_viewed", value)
        }
        get() {
            return sharedPreferences.get("onboarding_analytics_dialog_viewed", false)
        }

    var hasSeenCrashReportingDialog: Boolean
        set(value) {
            sharedPreferences.put("crash_reporting_dialog_viewed", value)
        }
        get() {
            return sharedPreferences.get("crash_reporting_dialog_viewed", false)
        }

    var hasOnboarded: Boolean
        set(value) {
            sharedPreferences.put("has_onboarded", value)
        }
        get() {
            return sharedPreferences.getBoolean("has_onboarded", false)
        }

    var currentLibraryTab: LibraryTab?
        set(value) {
            sharedPreferences.put("library_tab_current", value?.name)
        }
        get() {
            return sharedPreferences.getString("library_tab_current", null)?.let { LibraryTab.valueOf(it) }
        }

    var mediaSessionArtwork: Boolean
        set(value) {
            sharedPreferences.put("media_session_artwork", value)
        }
        get() {
            return sharedPreferences.getBoolean("media_session_artwork", true)
        }

    // Widgets

    var widgetDarkMode: Boolean
        set(value) {
            sharedPreferences.put("widget_dark_mode", value)
        }
        get() {
            return sharedPreferences.getBoolean("widget_dark_mode", false)
        }

    var widgetBackgroundTransparency: Int
        set(value) {
            sharedPreferences.put("widget_background_opacity", value)
        }
        get() {
            return sharedPreferences.getInt("widget_background_opacity", 100)
        }

    // Debugging

    var debugFileLogging: Boolean
        set(value) {
            sharedPreferences.put("pref_file_logging", value)
        }
        get() {
            return sharedPreferences.getBoolean("pref_file_logging", false)
        }

    // Search

    var searchFilterArtists: Boolean
        set(value) {
            sharedPreferences.put("search_filter_artists", value)
        }
        get() {
            return sharedPreferences.getBoolean("search_filter_artists", true)
        }

    var searchFilterAlbums: Boolean
        set(value) {
            sharedPreferences.put("search_filter_albums", value)
        }
        get() {
            return sharedPreferences.getBoolean("search_filter_albums", true)
        }

    var searchFilterSongs: Boolean
        set(value) {
            sharedPreferences.put("search_filter_songs", value)
        }
        get() {
            return sharedPreferences.getBoolean("search_filter_songs", true)
        }

    // Playlists

    var ignorePlaylistDuplicates: Boolean
        set(value) {
            sharedPreferences.put("playlist_ignore_duplicates", value)
        }
        get() {
            return sharedPreferences.getBoolean("playlist_ignore_duplicates", false)
        }

    // Sleep Timer

    var sleepTimerPlayToEnd: Boolean
        set(value) {
            sharedPreferences.put("sleep_timer_play_to_end", value)
        }
        get() {
            return sharedPreferences.getBoolean("sleep_timer_play_to_end", false)
        }

    // Playback

    var retainShuffleOnNewQueue: Boolean
        set(value) {
            sharedPreferences.put("pref_retain_shuffle_on_new_queue", value)
        }
        get() {
            return sharedPreferences.get("pref_retain_shuffle_on_new_queue", false)
        }

    var allLibraryTabs: List<LibraryTab>
        set(value) {
            sharedPreferences.put("pref_library_tabs_all", value.joinToString(","))
        }
        get() {
            return sharedPreferences.getString("pref_library_tabs_all", null)
                ?.split(",")
                ?.map { LibraryTab.valueOf(it) }
                ?: LibraryTab.values().toList()
        }

    var enabledLibraryTabs: List<LibraryTab>
        set(value) {
            sharedPreferences.put("pref_library_tabs_enabled", value.joinToString(","))
        }
        get() {
            return sharedPreferences.getString("pref_library_tabs_enabled", LibraryTab.values().joinToString(","))
                ?.split(",")
                ?.mapNotNull {
                    try {
                        LibraryTab.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                .orEmpty()
        }

    var showHomeOnLaunch: Boolean
        set(value) {
            sharedPreferences.put("pref_show_home_on_launch", value)
        }
        get() {
            return sharedPreferences.get("pref_show_home_on_launch", false)
        }

    var mediaImportFrequency: Int = sharedPreferences.getString("pref_media_rescan_frequency", "0")?.toInt() ?: 0
    var lastMediaImportDate: Date?
        set(value) {
            sharedPreferences.put("pref_media_last_rescan_date", value?.time)
        }
        get() {
            val time = sharedPreferences.getLong("pref_media_last_rescan_date", -1)
            if (time != -1L) {
                return Date(time)
            }
            return null
        }
}

enum class LibraryTab {
    Genres,
    Playlists,
    Artists,
    Albums,
    Songs
}
