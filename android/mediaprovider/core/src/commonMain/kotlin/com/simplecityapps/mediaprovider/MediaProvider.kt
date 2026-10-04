package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

interface MediaProvider {
    val type: MediaProviderType

    /**
     * @param existingSongs the songs this provider imported last time, so it can skip expensive work for files that haven't changed
     */
    fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>>

    /**
     * Maps [existingSongs] stored under an identity this provider no longer produces to the path [findSongs] now returns
     * for the same file, so an upgrade keeps their history. Songs it can't match are left out, and the import removes
     * them as missing. Runs before every song import, so it must return nothing once no old identities are left.
     */
    suspend fun remapLegacySongs(existingSongs: List<Song>): List<SongPathRemap> = emptyList()

    /**
     * The songs the last [findSongs] found are stored: called once the import has saved them, and not when it fails, so
     * a provider can note what it imported (iOS's local files remember their listing, to import again only on a change).
     */
    suspend fun songsStored() {}

    /**
     * The roots the last [findSongs] couldn't read, as path prefixes each ending in a separator: a volume that isn't
     * mounted, a folder whose access was lost. The import keeps the stored songs under them rather than deleting them as
     * missing ([DeleteGuard]).
     */
    val unreadableRoots: Set<String> get() = emptySet()

    fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>>
}

/**
 * A source whose [findSongs] trusts an index of its files (MediaStore, for this device's folders), which is quick but can
 * miss files the index skipped or lost. When the user asks for an import (a rescan, a change of folders, the first one),
 * the importer calls [findSongsThoroughly] instead, which looks in every folder itself as well.
 */
interface IndexedMediaProvider : MediaProvider {
    /** Every song [findSongs] finds, and those in the source's folders its index doesn't list. */
    fun findSongsThoroughly(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>>
}

/**
 * A source that can list just what changed since a time, so a sync doesn't fetch the whole library again (#771). What
 * [findSongsChangedSince] finds is stored over the last import without removing anything: a song deleted on the source
 * leaves the library at the next full [findSongs].
 */
interface IncrementalMediaProvider : MediaProvider {
    /**
     * The songs added to the source or changed on it at or after [since], plus those of [existingSongs] whose favourite
     * changed on it, which a server doesn't count as a change to the song; songs it no longer has aren't reported.
     */
    fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>>
}
