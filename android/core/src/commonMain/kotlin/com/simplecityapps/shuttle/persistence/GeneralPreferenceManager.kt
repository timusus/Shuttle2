package com.simplecityapps.shuttle.persistence

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@SingleIn(AppScope::class)
class GeneralPreferenceManager @Inject constructor(
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
                ?.let(::libraryTabs)
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
                ?.let(::libraryTabs)
                .orEmpty()
        }

    /** The tabs named in comma-separated [names], once each; a name this version doesn't know (a newer backup's) is skipped. */
    private fun libraryTabs(names: String): List<LibraryTab> = names.split(",").mapNotNull { name -> LibraryTab.entries.firstOrNull { it.name == name } }.distinct()

    /** The first-run source setup (iOS) was finished or skipped, so it never opens by itself again. */
    var sourceSetupCompleted: Boolean
        set(value) {
            store.putBoolean("source_setup_completed", value)
        }
        get() {
            return store.getBoolean("source_setup_completed", false)
        }
}

enum class LibraryTab {
    Songs,
    Albums,
    Artists,
    Playlists,
    Genres,
    Folders;

    companion object {
        /** Folders is opt-in: most people browse by tag, and it adds a sixth tab. */
        val defaultEnabled: List<LibraryTab> get() = entries - Folders
    }
}
