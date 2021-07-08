package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences
import java.util.*

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
            return sharedPreferences.get("changelog_show_on_launch", false)
        }

    var lastViewedChangelogVersion: String?
        set(value) {
            sharedPreferences.put("last_viewed_changelog_version", value)
        }
        get() {
            return sharedPreferences.getString("last_viewed_changelog_version", null)
        }

    var lastViewedTrialDialog: Date
        set(value) {
            sharedPreferences.put("last_viewed_trial_dialog", value.time)
        }
        get() {
            return Date(sharedPreferences.getLong("last_viewed_trial_dialog", Date().time))
        }

    var hasSeenThankYouDialog: Boolean
        set(value) {
            sharedPreferences.put("thank_you_dialog_viewed", value)
        }
        get() {
            return sharedPreferences.get("thank_you_dialog_viewed", false)
        }

    enum class Theme {
        DayNight, Light, Dark
    }

    var themeBase: Theme
        set(value) {
            sharedPreferences.put("pref_theme", value.ordinal.toString())
        }
        get() {
            return Theme.values()[sharedPreferences.get("pref_theme", "0").toInt()]
        }

    enum class Accent {
        Default, Orange, Cyan, Purple, Green, Amber
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
            return sharedPreferences.get("pref_crash_reporting", true)
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

    var hasOnboarded: Boolean
        set(value) {
            sharedPreferences.put("has_onboarded", value)
        }
        get() {
            return sharedPreferences.getBoolean("has_onboarded", false)
        }

    var libraryTabIndex: Int
        set(value) {
            sharedPreferences.put("library_tab_index", value)
        }
        get() {
            return sharedPreferences.getInt("library_tab_index", -1)
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
}