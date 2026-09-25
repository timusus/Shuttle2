package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongDataUpdate
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private fun createSongData(album: String) = SongData(
        name = "Song",
        track = 1,
        disc = 1,
        duration = 180_000,
        year = null,
        genres = emptyList(),
        path = "/music/$album.mp3",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = album,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = Date(0),
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private class FakeSongDataDao(private val songs: Flow<List<SongData>>) : SongDataDao() {
        override fun getAllSongData(): Flow<List<SongData>> = songs

        override suspend fun get(): List<SongData> = throw NotImplementedError()

        override suspend fun insert(songData: List<SongData>): List<Long> = throw NotImplementedError()

        override suspend fun update(songData: List<SongDataUpdate>): Int = throw NotImplementedError()

        override suspend fun update(songData: SongDataUpdate): Int = throw NotImplementedError()

        override suspend fun delete(songData: List<SongData>): Int = throw NotImplementedError()

        override suspend fun idForPath(path: String): Long? = throw NotImplementedError()

        override suspend fun updatePath(
            id: Long,
            path: String
        ): Int = throw NotImplementedError()

        override suspend fun movePlaylistEntries(
            fromSongIds: List<Long>,
            songId: Long
        ) = throw NotImplementedError()

        override suspend fun incrementPlayCount(
            id: Long,
            lastCompleted: Date
        ) = throw NotImplementedError()

        override suspend fun updatePlaybackPosition(
            id: Long,
            playbackPosition: Int,
            lastPlayed: Date
        ) = throw NotImplementedError()

        override suspend fun setExcluded(
            ids: List<Long>,
            blacklisted: Boolean
        ): Int = throw NotImplementedError()

        override suspend fun clearExcludeList() = throw NotImplementedError()

        override suspend fun deleteAll(mediaProviderType: MediaProviderType) = throw NotImplementedError()

        override suspend fun deleteAll(songData: List<SongData>): Int = throw NotImplementedError()

        override suspend fun delete(id: Long) = throw NotImplementedError()
    }
}
