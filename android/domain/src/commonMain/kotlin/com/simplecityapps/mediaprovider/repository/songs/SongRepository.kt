package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface SongRepository {
    fun getSongs(query: SongQuery): Flow<List<Song>?>

    /**
     * The songs matching [query] as stored now, including any write that has returned. [getSongs] can lag a write (the local
     * repository shares one song list across its collectors and requeries it only after the write), so read this when the next
     * step depends on what was just written.
     */
    suspend fun loadSongs(query: SongQuery): List<Song> = getSongs(query).filterNotNull().first()

    /**
     * Every song stored for [mediaProviderType], unfiltered: what an import diffs against, so it sees the songs a minimum
     * track length hides in the library.
     */
    suspend fun loadProviderSongs(mediaProviderType: MediaProviderType): List<Song> = loadSongs(SongQuery.All(includeExcluded = true, providerType = mediaProviderType))

    /**
     * The ids of the songs whose stored metadata a write has just replaced ([update], and the updates of
     * [insertUpdateAndDelete]: a tag edit, a rescan, a remote sync), once per write, after it's stored. Play counts,
     * positions, exclusion and removals aren't reported here.
     */
    val updatedSongIds: Flow<Set<Long>>

    suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    )

    suspend fun update(song: Song): Int

    suspend fun update(songs: List<Song>)

    suspend fun remove(song: Song)

    suspend fun removeAll(mediaProviderType: MediaProviderType)

    suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int>

    /**
     * Applies each remap that doesn't clash with a [mediaProviderType] song already stored under its path, in one
     * transaction.
     *
     * @return the remaps applied
     */
    suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap>

    suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    )

    /** Sets [song]'s playback position to its own duration and increments its play count, as one write. */
    suspend fun recordPlayedThrough(song: Song)

    suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    )

    suspend fun clearExcludeList()

    /**
     * Makes [songs] favourites, or with [favourite] false stops them being ones. Stored with the song whatever its provider,
     * so it's where a provider's own favourites (Jellyfin, Emby, Plex) will be written back and read in from (#497). A
     * song that already carries a [Song.favouritedAt] is set to that exact time rather than now, so restoring one just
     * removed (an Undo) gets its original place back instead of jumping to the top (#564).
     */
    suspend fun setFavourite(
        songs: List<Song>,
        favourite: Boolean
    )

    /** The ids of the favourite songs (excluded ones included), again whenever they change. */
    fun getFavouriteSongIds(): Flow<Set<Long>> = getSongs(SongQuery.Favourites)
        .filterNotNull()
        .map { songs -> songs.mapTo(mutableSetOf()) { song -> song.id } }
}
