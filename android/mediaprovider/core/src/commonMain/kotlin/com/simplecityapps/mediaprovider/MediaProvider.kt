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
     * The paths of the files the last [findSongs] left unread, because reading one crashed the app before (#840). A stored
     * song among them keeps its row; a new file isn't added. The import records how many, for Sources to say.
     */
    val skippedFiles: Set<String> get() = emptySet()

    /**
     * The roots the last [findSongs] couldn't read, as path prefixes each ending in a separator: a volume that isn't
     * mounted, a folder whose access was lost. The import keeps the stored songs under them rather than deleting them as
     * missing ([DeleteGuard]).
     */
    val unreadableRoots: Set<String> get() = emptySet()

    /**
     * The source's playlists, each holding those of [existingSongs] it lists. A remote server's [FlowEvent.Success] is its
     * listing, which the importer makes the playlists stored from it match ([ImportedPlaylistStore.reconcilePlaylists]): it
     * must name every playlist the server listed, those whose songs it couldn't read as [MediaImporter.PlaylistListing.unread],
     * and come [FlowEvent.Success.missing] as many as the listing left out. A listing that failed is a [FlowEvent.Failure].
     *
     * [knownVersions] is the [version][MediaImporter.PlaylistListing.versions] of each playlist as last stored, by its
     * [MediaImporter.PlaylistUpdateData.externalId]: a server that reports the same version for one now can skip reading its
     * songs and list it as [unchanged][MediaImporter.PlaylistListing.unchanged] instead.
     */
    fun findPlaylists(
        existingSongs: List<Song>,
        knownVersions: Map<String, String> = emptyMap()
    ): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>>
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
 * A source that lists its songs a page at a time by offset, so a song going mid-listing shifts the pages and can skip
 * another that's still there (#934). The importer removes a song only if a second [findSongPaths] listing lacks it too.
 */
interface PathListingMediaProvider : MediaProvider {
    /**
     * The path of every song the source holds, fetched as lightly as the source allows (ids alone, no tags). As a
     * [findSongs] listing is, it's [missing][FlowEvent.Success.missing] as many as the source says it holds beyond those it
     * listed, and a [FlowEvent.Failure] if any page failed: the importer removes nothing against either.
     */
    fun findSongPaths(): Flow<FlowEvent<List<String>, MessageProgress>>
}

/**
 * A source that can list just what changed since a time, so a sync doesn't fetch the whole library again (#771). What
 * [findSongsChangedSince] finds is stored over the last import; it can't say what's gone, so the importer asks
 * [countSongs] and, if the count isn't the one it expects, [findSongPaths] which songs the source still holds, and removes
 * the others (#845), rather than leaving them to the next full [findSongs].
 */
interface IncrementalMediaProvider : PathListingMediaProvider {
    /** How many songs the source holds, in one cheap request, or null if it can't be read. */
    suspend fun countSongs(): Int?

    /**
     * The songs added to the source or changed on it at or after [since], plus those of [existingSongs] whose favourite
     * changed on it, which a server doesn't count as a change to the song; songs it no longer has aren't reported.
     */
    fun findSongsChangedSince(
        existingSongs: List<Song>,
        since: Instant
    ): Flow<FlowEvent<List<Song>, MessageProgress>>
}
