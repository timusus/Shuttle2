package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.withAlbumIdentities
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class LocalAlbumRepositoryTest {
    @Test
    fun `identical re-emission of songs does not produce a new album emission`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val repository = LocalAlbumRepository(scope = backgroundScope, songs = songsFlow.map { it.map(SongData::toSong).withAlbumIdentities() })

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
        val repository = LocalAlbumRepository(scope = backgroundScope, songs = songsFlow.map { it.map(SongData::toSong).withAlbumIdentities() })

        songsFlow.emit(
            listOf(
                createSongData(album = "Album A", track = 1, playCount = 3),
                createSongData(album = "Album A", track = 2, playCount = 0),
                createSongData(album = "Album A", track = 3, playCount = 2)
            )
        )

        repository.getAlbums(AlbumQuery.All()).first().single().playCount shouldBe 5
    }
}
