package com.simplecityapps.shuttle.persistence

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What each source's imports left behind: sync cursors, playlist state, held deletes, reachability and tag-read quarantine. */
@SingleIn(AppScope::class)
class SourceStateStore @Inject constructor(
    private val store: KeyValueStore
) {
    /**
     * The version of the tags (`SONG_TAGS_VERSION`) the songs of [source] (a media provider type's name) were
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

    /** When the last successful sync of [source] started, taken before its request so a change made during it isn't missed. */
    fun lastSyncStart(source: String): Instant? = store.getInstant("last_sync_start_$source")

    fun setLastSyncStart(
        source: String,
        start: Instant?
    ) = store.putInstant("last_sync_start_$source", start)

    /** Each playlist's server version when last read in full, by external id, so an unchanged playlist isn't read again. */
    fun playlistVersions(source: String): Map<String, String> = store.getString("playlist_versions_$source", null)
        ?.lineSequence()
        ?.mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { (id, version) -> id to version } }
        ?.toMap()
        .orEmpty()

    fun setPlaylistVersions(
        source: String,
        versions: Map<String, String>
    ) = store.putString("playlist_versions_$source", versions.entries.joinToString("\n") { (id, version) -> "$id\t$version" }.ifEmpty { null })

    /** Songs each playlist held on the server when last read, by external id, so a re-read keeps songs added in S2. */
    fun playlistServerSongs(source: String): Map<String, List<String>> = store.getString("playlist_server_songs_$source", null)
        ?.lineSequence()
        ?.mapNotNull { line -> line.split('\t').takeIf { it.isNotEmpty() && it[0].isNotEmpty() }?.let { fields -> fields[0] to fields.drop(1) } }
        ?.toMap()
        .orEmpty()

    fun setPlaylistServerSongs(
        source: String,
        songs: Map<String, List<String>>
    ) = store.putString("playlist_server_songs_$source", songs.entries.joinToString("\n") { (id, paths) -> (listOf(id) + paths).joinToString("\t") }.ifEmpty { null })

    /** Playlist edits not yet sent to [source]'s server, oldest first; kept across restarts so an offline edit is sent later. */
    fun pendingPlaylistEdits(source: String): String? = store.getString("pending_playlist_edits_$source", null)

    fun setPendingPlaylistEdits(
        source: String,
        edits: String?
    ) = store.putString("pending_playlist_edits_$source", edits?.ifEmpty { null })

    /** When the last successful full sync of [source] started: the one that also removes songs the server no longer has. */
    fun lastFullSyncStart(source: String): Instant? = store.getInstant("last_full_sync_start_$source")

    fun setLastFullSyncStart(
        source: String,
        start: Instant?
    ) = store.putInstant("last_full_sync_start_$source", start)

    /**
     * The ids of the songs of [source] (a media provider type's name) its last complete listing (a full import's, or an
     * incremental sync's path listing) held back as a mass removal (`DeleteGuard`), which the next full import deletes if
     * it finds them gone too. Kept across restarts: that import
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

    /** [listingShortfall] now and each time it changes. */
    fun observeListingShortfall(source: String): Flow<Int> = store.changes("listing_shortfall_$source").map { listingShortfall(source) }.distinctUntilChanged()

    /** Album key version the stored keys were last moved to; 0 until they're moved after an import with current tags. */
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

    /** Emits [lastMediaImportDate] now and on each change, so a screen shows an import's end as it's written. */
    fun observeLastMediaImportDate(): Flow<Instant?> = store.changes(LAST_MEDIA_IMPORT_DATE).map { lastMediaImportDate }.distinctUntilChanged()

    /** How the last import of [source] ended, kept across restarts so Sources still shows an unreachable server. */
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

    /** When an import of [source] last completed successfully, for its row in Sources. */
    fun sourceUpdated(source: String): Instant? = store.getInstant("source_updated_$source")

    fun setSourceUpdated(
        source: String,
        updated: Instant?
    ) = store.putInstant("source_updated_$source", updated)

    /** [sourceUpdated] now and each time it changes. */
    fun observeSourceUpdated(source: String): Flow<Instant?> = store.changes("source_updated_$source").map { sourceUpdated(source) }.distinctUntilChanged()

    /** Forgets what Sources kept of [source]'s imports once the source is removed or forgotten. */
    fun clearSourceState(source: String) {
        store.edit {
            remove("source_error_$source")
            remove("source_checked_at_$source")
            remove("source_updated_$source")
            remove("listing_shortfall_$source")
            remove("skipped_files_$source")
        }
    }

    /** Files whose native tag read crashed the app, by `path|size|lastModified`; an edited file is another key, so it's retried. */
    fun tagReadQuarantine(): Set<String> = keySet(TAG_READ_QUARANTINE)

    fun quarantineTagRead(key: String) = putKeySet(TAG_READ_QUARANTINE, tagReadQuarantine() + key)

    /** Files a tag read was in flight on alone when the app died, where Android can't say it crashed (before 11): a second time quarantines it. */
    fun tagReadStrikes(): Set<String> = keySet(TAG_READ_STRIKES)

    fun setTagReadStrikes(keys: Set<String>) = putKeySet(TAG_READ_STRIKES, keys)

    /** Reads every quarantined file again: Sources' retry. */
    fun clearTagReadQuarantine() {
        store.edit {
            remove(TAG_READ_QUARANTINE)
            remove(TAG_READ_STRIKES)
        }
    }

    /** How many files the last full import of [source] left unread, for Sources to show. */
    fun skippedFiles(source: String): Int = store.getInt("skipped_files_$source", 0)

    fun setSkippedFiles(
        source: String,
        count: Int
    ) = store.putInt("skipped_files_$source", count)

    /** [skippedFiles] now and each time it changes. */
    fun observeSkippedFiles(source: String): Flow<Int> = store.changes("skipped_files_$source").map { skippedFiles(source) }.distinctUntilChanged()

    // One key per line: a path can hold any other separator
    private fun keySet(key: String): Set<String> = store.getString(key, null)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    private fun putKeySet(
        key: String,
        keys: Set<String>
    ) = store.putString(key, keys.takeIf { it.isNotEmpty() }?.joinToString("\n"))

    private companion object {
        const val LAST_MEDIA_IMPORT_DATE = "pref_media_last_rescan_date"
        const val TAG_READ_QUARANTINE = "tag_read_quarantine"
        const val TAG_READ_STRIKES = "tag_read_strikes"
    }
}

/** How a source's import ended at [checkedAt]: [error] is its message when the source couldn't be imported from, null when it went through. */
data class SourceReachability(
    val error: String?,
    val checkedAt: Instant
)

internal fun KeyValueStore.getInstant(key: String): Instant? {
    val epochMillis = getLong(key, -1)
    return if (epochMillis != -1L) Instant.fromEpochMilliseconds(epochMillis) else null
}

/** Stored as epoch milliseconds; null removes the value. */
internal fun KeyValueStore.putInstant(
    key: String,
    value: Instant?
) = edit { if (value == null) remove(key) else putLong(key, value.toEpochMilliseconds()) }
