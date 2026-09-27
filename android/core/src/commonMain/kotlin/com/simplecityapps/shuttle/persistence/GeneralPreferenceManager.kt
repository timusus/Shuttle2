package com.simplecityapps.shuttle.persistence

import kotlin.time.Instant

class GeneralPreferenceManager(
    private val store: KeyValueStore
) {
    var previousVersionCode: Int
        set(value) {
            store.putInt("previous_version_code", value)
        }
        get() {
            return store.getInt("previous_version_code", -1)
        }

    var showChangelogOnLaunch: Boolean
        set(value) {
            store.putBoolean("changelog_show_on_launch", value)
        }
        get() {
            return store.getBoolean("changelog_show_on_launch", true)
        }

    var lastViewedChangelogVersion: String?
        set(value) {
            store.putString("last_viewed_changelog_version", value)
        }
        get() {
            return store.getString("last_viewed_changelog_version", null)
        }

    var appPurchasedDate: Instant?
        set(value) {
            store.putInstant("app_purchased_date", value)
        }
        get() {
            return store.getInstant("app_purchased_date")
        }

    var lastViewedRatingFlow: Instant?
        set(value) {
            store.putInstant("last_viewed_rating_flow", value)
        }
        get() {
            return store.getInstant("last_viewed_rating_flow")
        }

    var artistListViewMode: String?
        set(value) {
            store.putString("pref_artist_view_mode", value)
        }
        get() {
            return store.getString("pref_artist_view_mode", null)
        }

    var albumListViewMode: String?
        set(value) {
            store.putString("pref_album_view_mode", value)
        }
        get() {
            return store.getString("pref_album_view_mode", null)
        }

    var currentLibraryTab: LibraryTab?
        set(value) {
            store.putString("library_tab_current", value?.name)
        }
        get() {
            return store.getString("library_tab_current", null)?.let { LibraryTab.valueOf(it) }
        }

    // Search

    var searchFilterArtists: Boolean
        set(value) {
            store.putBoolean("search_filter_artists", value)
        }
        get() {
            return store.getBoolean("search_filter_artists", true)
        }

    var searchFilterAlbums: Boolean
        set(value) {
            store.putBoolean("search_filter_albums", value)
        }
        get() {
            return store.getBoolean("search_filter_albums", true)
        }

    var searchFilterSongs: Boolean
        set(value) {
            store.putBoolean("search_filter_songs", value)
        }
        get() {
            return store.getBoolean("search_filter_songs", true)
        }

    var searchFilterGenres: Boolean
        set(value) {
            store.putBoolean("search_filter_genres", value)
        }
        get() {
            return store.getBoolean("search_filter_genres", true)
        }

    var searchFilterPlaylists: Boolean
        set(value) {
            store.putBoolean("search_filter_playlists", value)
        }
        get() {
            return store.getBoolean("search_filter_playlists", true)
        }

    /** Recent search queries, newest first. */
    var recentSearches: List<String>
        set(value) {
            store.putString("search_recent", value.joinToString("\n"))
        }
        get() {
            return store.getString("search_recent", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        }

    // Sleep Timer

    var sleepTimerPlayToEnd: Boolean
        set(value) {
            store.putBoolean("sleep_timer_play_to_end", value)
        }
        get() {
            return store.getBoolean("sleep_timer_play_to_end", false)
        }

    var allLibraryTabs: List<LibraryTab>
        set(value) {
            store.putString("pref_library_tabs_all", value.joinToString(","))
        }
        get() {
            return store.getString("pref_library_tabs_all", null)
                ?.split(",")
                ?.map { LibraryTab.valueOf(it) }
                // Tabs added since the user last reordered go at the end
                ?.let { stored -> stored + (LibraryTab.entries - stored.toSet()) }
                ?: LibraryTab.entries.toList()
        }

    var enabledLibraryTabs: List<LibraryTab>
        set(value) {
            store.putString("pref_library_tabs_enabled", value.joinToString(","))
        }
        get() {
            return store.getString("pref_library_tabs_enabled", LibraryTab.defaultEnabled.joinToString(","))
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

    // Songs imported via MediaStore before their tags were read from the file carry MediaStore's tag values (wrong for
    // Matroska and some UTF-8 tags, no ReplayGain at all), and an unchanged file isn't read again, so the first MediaStore
    // import after the upgrade reads every file once and then sets this
    var mediaStoreFileTagsBackfilled: Boolean
        set(value) {
            store.putBoolean("media_store_file_tags_backfilled", value)
        }
        get() {
            return store.getBoolean("media_store_file_tags_backfilled", false)
        }

    var lastMediaImportDate: Instant?
        set(value) {
            store.putInstant("pref_media_last_rescan_date", value)
        }
        get() {
            return store.getInstant("pref_media_last_rescan_date")
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

private fun KeyValueStore.getInstant(key: String): Instant? {
    val epochMillis = getLong(key, -1)
    return if (epochMillis != -1L) Instant.fromEpochMilliseconds(epochMillis) else null
}

/** Stored as epoch milliseconds; null removes the value. */
private fun KeyValueStore.putInstant(
    key: String,
    value: Instant?
) = edit { if (value == null) remove(key) else putLong(key, value.toEpochMilliseconds()) }
