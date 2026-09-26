package com.simplecityapps.shuttle.persistence

import android.content.SharedPreferences
import java.util.Date

class GeneralPreferenceManager(
    private val sharedPreferences: SharedPreferences
) {
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

    var currentLibraryTab: LibraryTab?
        set(value) {
            sharedPreferences.put("library_tab_current", value?.name)
        }
        get() {
            return sharedPreferences.getString("library_tab_current", null)?.let { LibraryTab.valueOf(it) }
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

    var searchFilterGenres: Boolean
        set(value) {
            sharedPreferences.put("search_filter_genres", value)
        }
        get() {
            return sharedPreferences.getBoolean("search_filter_genres", true)
        }

    var searchFilterPlaylists: Boolean
        set(value) {
            sharedPreferences.put("search_filter_playlists", value)
        }
        get() {
            return sharedPreferences.getBoolean("search_filter_playlists", true)
        }

    /** Recent search queries, newest first. */
    var recentSearches: List<String>
        set(value) {
            sharedPreferences.put("search_recent", value.joinToString("\n"))
        }
        get() {
            return sharedPreferences.getString("search_recent", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        }

    // Sleep Timer

    var sleepTimerPlayToEnd: Boolean
        set(value) {
            sharedPreferences.put("sleep_timer_play_to_end", value)
        }
        get() {
            return sharedPreferences.getBoolean("sleep_timer_play_to_end", false)
        }

    var allLibraryTabs: List<LibraryTab>
        set(value) {
            sharedPreferences.put("pref_library_tabs_all", value.joinToString(","))
        }
        get() {
            return sharedPreferences.getString("pref_library_tabs_all", null)
                ?.split(",")
                ?.map { LibraryTab.valueOf(it) }
                // Tabs added since the user last reordered go at the end
                ?.let { stored -> stored + (LibraryTab.entries - stored.toSet()) }
                ?: LibraryTab.entries.toList()
        }

    var enabledLibraryTabs: List<LibraryTab>
        set(value) {
            sharedPreferences.put("pref_library_tabs_enabled", value.joinToString(","))
        }
        get() {
            return sharedPreferences.getString("pref_library_tabs_enabled", LibraryTab.defaultEnabled.joinToString(","))
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

    // Songs imported via MediaStore before ReplayGain tags were read for them have null ReplayGain values that look
    // just like untagged files, so the first MediaStore import after the upgrade reads every file once and then sets this
    var mediaStoreReplayGainBackfilled: Boolean
        set(value) {
            sharedPreferences.put("media_store_replay_gain_backfilled", value)
        }
        get() {
            return sharedPreferences.get("media_store_replay_gain_backfilled", false)
        }

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
    Songs,
    Folders;

    companion object {
        /** Folders is opt-in: most people browse by tag, and it adds a sixth tab. */
        val defaultEnabled: List<LibraryTab> get() = entries - Folders
    }
}
