package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalAlbumArtistRepositoryTest {
    @Test
    fun `an album artist's play count is the sum of their songs' play counts`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val repository = LocalAlbumArtistRepository(scope = backgroundScope, songDataDao = FakeSongDataDao(songsFlow))

        songsFlow.emit(
            listOf(
                createSongData(album = "Album A", albumArtist = "Played", track = 1, playCount = 3),
                createSongData(album = "Album A", albumArtist = "Played", track = 2, playCount = 0),
                createSongData(album = "Album B", albumArtist = "Played", track = 1, playCount = 2),
                createSongData(album = "Album C", albumArtist = "Never played", track = 1, playCount = 0)
            )
        )

        repository.getAlbumArtists(AlbumArtistQuery.All()).first().associate { it.name to it.playCount } shouldBe
            mapOf("Played" to 5, "Never played" to 0)
    }
}
