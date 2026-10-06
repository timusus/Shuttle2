package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A song repository answering every query with [songs], for the sources that pick a song to find artwork through. */
internal class FakeSongRepository(private val songs: List<Song>) : SongRepository {
    override fun getSongs(query: SongQuery): Flow<List<Song>?> = flowOf(songs)

    override fun countSongs(): Flow<Int> = flowOf(songs.size)

    override fun countSongsByProvider(): Flow<Map<MediaProviderType, Int>> = flowOf(songs.groupingBy { it.mediaProvider }.eachCount())

    override suspend fun loadLyrics(songId: Long): String? = null

    override suspend fun insert(
        songs: List<Song>,
        mediaProviderType: MediaProviderType
    ) {}

    override suspend fun update(song: Song): Int = 0

    override suspend fun update(songs: List<Song>) {}

    override suspend fun remove(song: Song) {}

    override suspend fun removeAll(mediaProviderType: MediaProviderType) {}

    override suspend fun insertUpdateAndDelete(
        inserts: List<Song>,
        updates: List<Song>,
        deletes: List<Song>,
        mediaProviderType: MediaProviderType
    ): Triple<Int, Int, Int> = Triple(0, 0, 0)

    override suspend fun remapPaths(
        remaps: List<SongPathRemap>,
        mediaProviderType: MediaProviderType
    ): List<SongPathRemap> = remaps

    override val updatedSongIds: Flow<Set<Long>> = flowOf()

    override suspend fun recordPlayedThrough(song: Song) {}

    override suspend fun setPlaybackPosition(
        song: Song,
        playbackPosition: Int
    ) {}

    override suspend fun setExcluded(
        songs: List<Song>,
        excluded: Boolean
    ) {}

    override suspend fun clearExcludeList() {}

    override suspend fun setFavourite(
        songs: List<Song>,
        favourite: Boolean
    ) {}
}
