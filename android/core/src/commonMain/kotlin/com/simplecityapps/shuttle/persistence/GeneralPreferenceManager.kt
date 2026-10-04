package com.simplecityapps.shuttle.persistence

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

    /**
     * The version of the tags (`MediaImporter.SONG_TAGS_VERSION`) the songs of [source] (a media provider type's name) were
     * last imported with, set when that source's import succeeds. Behind the current version its songs lack tags this
     * build reads, so the MediaStore provider reads unchanged files again rather than keeping their stored values.
     */
    fun songTagsVersion(source: String): Int = store.getInt("song_tags_version_$source", 0)

    fun setSongTagsVersion(
        source: String,
        version: Int
    ) {
        store.putInt("song_tags_version_$source", version)
    }

    /**
     * When the last successful sync of [source] (a media provider type's name) started (#771): the next incremental sync
     * asks the server for what changed since then. Taken before the request, so a change made while it ran is fetched
     * again rather than missed.
     */
    fun lastSyncStart(source: String): Instant? = store.getInstant("last_sync_start_$source")

    fun setLastSyncStart(
        source: String,
        start: Instant?
    ) = store.putInstant("last_sync_start_$source", start)

    /** When the last successful full sync of [source] started: the one that also removes songs the server no longer has. */
    fun lastFullSyncStart(source: String): Instant? = store.getInstant("last_full_sync_start_$source")

    fun setLastFullSyncStart(
        source: String,
        start: Instant?
    ) = store.putInstant("last_full_sync_start_$source", start)

    /**
     * The ids of the songs of [source] (a media provider type's name) its last full import held back as a mass removal
     * (`DeleteGuard`), which the next full import deletes if it finds them gone too. Kept across restarts: that import
     * usually runs in another process. Stored sorted, as base-36 gaps between ids, so a large removal stays small.
     */
    fun heldDeletes(source: String): Set<Long> {
        val gaps = store.getString(heldDeletesKey(source), null)?.split(",")?.map { gap -> gap.toLongOrNull(36) } ?: return emptySet()
        // Unreadable, it holds nothing: a mass removal is held one more pass rather than a wrong song deleted
        if (gaps.any { it == null }) return emptySet()
        return gaps.filterNotNull().runningReduce(Long::plus).toSet()
    }

    fun setHeldDeletes(
        source: String,
        songIds: Set<Long>
    ) {
        store.edit {
            if (songIds.isEmpty()) {
                remove(heldDeletesKey(source))
            } else {
                putString(heldDeletesKey(source), (listOf(0L) + songIds.sorted()).zipWithNext { previous, id -> (id - previous).toString(36) }.joinToString(","))
            }
        }
    }

    private fun heldDeletesKey(source: String) = "held_deletes_$source"

    /**
     * How many songs short of its own total the last full listing of [source] (a media provider type's name) came to:
     * a server that comes up short by as many every time (Jellyfin counts rows it can't read) is still listing everything
     * it can (`DeleteGuard`).
     */
    fun listingShortfall(source: String): Int = store.getInt("listing_shortfall_$source", 0)

    fun setListingShortfall(
        source: String,
        missing: Int
    ) = store.putInt("listing_shortfall_$source", missing)

    /**
     * The album key version the stored album keys (play history, pinned downloads) were last moved to (#637): 0 before
     * the album identity rule, so they're moved once, after the first import that leaves every source's tags current.
     */
    var albumKeysVersion: Int
        set(value) {
            store.putInt("album_keys_version", value)
        }
        get() {
            return store.getInt("album_keys_version", 0)
        }

    /** The tags version the one launch re-import for outdated songs was last started for, so it starts once per version. */
    var songTagsRescanVersion: Int
        set(value) {
            store.putInt("song_tags_rescan_version", value)
        }
        get() {
            return store.getInt("song_tags_rescan_version", 0)
        }

    var lastMediaImportDate: Instant?
        set(value) {
            store.putInstant(LAST_MEDIA_IMPORT_DATE, value)
        }
        get() {
            return store.getInstant(LAST_MEDIA_IMPORT_DATE)
        }

    /** [lastMediaImportDate] now and each time it changes, so a screen shows an import's end as it's written (#648). */
    fun observeLastMediaImportDate(): Flow<Instant?> = store.changes(LAST_MEDIA_IMPORT_DATE).map { lastMediaImportDate }.distinctUntilChanged()

    /**
     * How the last import of [source] (a media provider type's name) ended and when, kept across restarts so Sources
     * still says a server couldn't be reached until an import gets through (#668). Null before any import has ended.
     */
    fun sourceReachability(source: String): SourceReachability? {
        val checkedAt = store.getInstant("source_checked_at_$source") ?: return null
        return SourceReachability(error = store.getString("source_error_$source", null), checkedAt = checkedAt)
    }

    fun setSourceReachability(
        source: String,
        reachability: SourceReachability
    ) {
        store.edit {
            if (reachability.error == null) remove("source_error_$source") else putString("source_error_$source", reachability.error)
            putLong("source_checked_at_$source", reachability.checkedAt.toEpochMilliseconds())
        }
    }

    /** [sourceReachability] now and each time an import of [source] ends. */
    fun observeSourceReachability(source: String): Flow<SourceReachability?> = store.changes("source_checked_at_$source").map { sourceReachability(source) }.distinctUntilChanged()

    /** The first-run source setup (iOS) was finished or skipped, so it never opens by itself again. */
    var sourceSetupCompleted: Boolean
        set(value) {
            store.putBoolean("source_setup_completed", value)
        }
        get() {
            return store.getBoolean("source_setup_completed", false)
        }

    private companion object {
        const val LAST_MEDIA_IMPORT_DATE = "pref_media_last_rescan_date"
    }
}

/** How a source's import ended at [checkedAt]: [error] is its message when the source couldn't be imported from, null when it went through. */
data class SourceReachability(
    val error: String?,
    val checkedAt: Instant
)

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

private fun KeyValueStore.getInstant(key: String): Instant? {
    val epochMillis = getLong(key, -1)
    return if (epochMillis != -1L) Instant.fromEpochMilliseconds(epochMillis) else null
}

/** Stored as epoch milliseconds; null removes the value. */
private fun KeyValueStore.putInstant(
    key: String,
    value: Instant?
) = edit { if (value == null) remove(key) else putLong(key, value.toEpochMilliseconds()) }
