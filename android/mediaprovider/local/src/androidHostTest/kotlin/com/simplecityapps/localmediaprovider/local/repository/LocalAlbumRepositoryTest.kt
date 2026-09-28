package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class LocalAlbumRepositoryTest {
    @Test
    fun `identical re-emission of songs does not produce a new album emission`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val dao = FakeSongDataDao(songsFlow)
        val repository = LocalAlbumRepository(scope = backgroundScope, songDataDao = dao)

        val emissions = mutableListOf<List<Album>>()
        backgroundScope.launch {
            repository.getAlbums(AlbumQuery.All()).collect { emissions.add(it) }
        }

        songsFlow.emit(listOf(createSongData(album = "Album A")))
        while (emissions.isEmpty()) yield()

        // Same content, new instances - simulates Room re-emitting after an unrelated table write.
        songsFlow.emit(listOf(createSongData(album = "Album A")))
        // Genuinely different content, to prove the collector is still live.
        songsFlow.emit(listOf(createSongData(album = "Album B")))
        while (emissions.size < 2) yield()

        emissions shouldHaveSize 2
        emissions[0].map { it.name } shouldBe listOf("Album A")
        emissions[1].map { it.name } shouldBe listOf("Album B")
    }

    @Test
    fun `an album's play count is the sum of its songs' play counts`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val repository = LocalAlbumRepository(scope = backgroundScope, songDataDao = FakeSongDataDao(songsFlow))

        songsFlow.emit(
            listOf(
                createSongData(album = "Album A", track = 1, playCount = 3),
                createSongData(album = "Album A", track = 2, playCount = 0),
                createSongData(album = "Album A", track = 3, playCount = 2)
            )
        )

        repository.getAlbums(AlbumQuery.All()).first().single().playCount shouldBe 5
    }

    private fun createSongData(
        album: String,
        track: Int = 1,
        playCount: Int = 0
    ) = SongData(
        name = "Song",
        track = track,
        disc = 1,
        duration = 180_000,
        year = null,
        genres = emptyList(),
        path = "/music/$album/$track.mp3",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = album,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(0),
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        playCount = playCount
    )

    private class FakeSongDataDao(private val songs: Flow<List<SongData>>) : SongDataDao() {
        override fun getAllSongData(): Flow<List<SongData>> = songs

        override fun getSongDataByIds(ids: List<Long>): Flow<List<SongData>> = throw NotImplementedError()

        override suspend fun get(): List<SongData> = throw NotImplementedError()

        override suspend fun insert(songData: List<SongData>): List<Long> = throw NotImplementedError()

        override suspend fun update(songData: List<SongDataUpdate>): Int = throw NotImplementedError()

        override suspend fun update(songData: SongDataUpdate): Int = throw NotImplementedError()

        override suspend fun delete(songData: List<SongData>): Int = throw NotImplementedError()

        override suspend fun idForPath(
            path: String,
            mediaProvider: MediaProviderType
        ): Long? = throw NotImplementedError()

        override suspend fun updatePath(
            id: Long,
            path: String
        ): Int = throw NotImplementedError()

        override suspend fun movePlaylistEntries(
            fromSongIds: List<Long>,
            songId: Long
        ) = throw NotImplementedError()

        override suspend fun updatePlaybackPosition(
            id: Long,
            playbackPosition: Int,
            lastPlayed: Instant
        ) = throw NotImplementedError()

        override suspend fun recordPlayedThrough(
            id: Long,
            playbackPosition: Int,
            now: Instant
        ) = throw NotImplementedError()

        override suspend fun setExcluded(
            ids: List<Long>,
            blacklisted: Boolean
        ): Int = throw NotImplementedError()

        override suspend fun clearExcludeList() = throw NotImplementedError()

        override suspend fun keepFavourite(
            fromSongIds: List<Long>,
            songId: Long
        ) = throw NotImplementedError()

        override suspend fun favourite(
            ids: List<Long>,
            now: Instant
        ): Int = throw NotImplementedError()

        override suspend fun favourite(
            id: Long,
            favouritedAt: Instant
        ): Int = throw NotImplementedError()

        override suspend fun unfavourite(ids: List<Long>): Int = throw NotImplementedError()

        override suspend fun enqueuePendingFavourite(pendingFavourite: PendingFavouriteData) = throw NotImplementedError()

        override suspend fun getPendingFavourites(): List<PendingFavouriteData> = throw NotImplementedError()

        override fun getFavouriteIds(): Flow<List<Long>> = throw NotImplementedError()

        override suspend fun deleteAll(mediaProviderType: MediaProviderType) = throw NotImplementedError()

        override suspend fun deleteAll(songData: List<SongData>): Int = throw NotImplementedError()

        override suspend fun delete(id: Long) = throw NotImplementedError()
    }
}
