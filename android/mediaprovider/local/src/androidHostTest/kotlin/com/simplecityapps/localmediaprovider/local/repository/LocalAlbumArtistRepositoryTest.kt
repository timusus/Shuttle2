package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
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

    @Test
    fun `a sample library groups into albums and album artists by the identity rule`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val albums = LocalAlbumRepository(scope = backgroundScope, songDataDao = FakeSongDataDao(songsFlow))
        val artists = LocalAlbumArtistRepository(scope = backgroundScope, songDataDao = FakeSongDataDao(songsFlow))

        songsFlow.emit(
            listOf(
                // Tagged: the album artist names it
                createSongData(album = "OK Computer", albumArtist = "Radiohead", track = 1),
                createSongData(album = "OK Computer", albumArtist = "Radiohead", track = 2),
                // Two releases of one name, told apart by their MusicBrainz ids
                createSongData(album = "Greatest Hits", albumArtist = "Queen", track = 1).copy(mbAlbumId = "gh1"),
                createSongData(album = "Greatest Hits", albumArtist = "Queen", track = 2).copy(mbAlbumId = "gh2", path = "/music/Queen/GH2/1.mp3"),
                // A compilation: Various Artists', not an album per track artist
                createSongData(album = "Now 100", track = 1).copy(albumArtist = null, artists = listOf("Adele"), compilation = true),
                createSongData(album = "Now 100", track = 2).copy(albumArtist = null, artists = listOf("Dua Lipa"), compilation = true),
                // Untagged, one artist over two disc folders: theirs, and one album
                createSongData(album = "Blonde", track = 1).copy(albumArtist = null, artists = listOf("Frank Ocean"), path = "/music/Blonde/CD1/1.mp3"),
                createSongData(album = "Blonde", track = 2).copy(albumArtist = null, artists = listOf("Frank Ocean feat. André 3000"), path = "/music/Blonde/CD2/1.mp3")
            )
        )

        albums.getAlbums(AlbumQuery.All()).first().map { it.name to it.albumArtist }.sortedBy { it.toString() } shouldBe listOf(
            "Blonde" to "Frank Ocean",
            "Greatest Hits" to "Queen",
            "Greatest Hits" to "Queen",
            "Now 100" to "Various Artists",
            "OK Computer" to "Radiohead"
        )
        artists.getAlbumArtists(AlbumArtistQuery.All()).first().associate { it.name to it.albumCount } shouldBe
            mapOf("Radiohead" to 1, "Queen" to 2, "Various Artists" to 1, "Frank Ocean" to 1)
    }

    @Test
    fun `the Artists list is the album artists, and featured and compilation artists are found as credited ones`() = runTest {
        val songsFlow = MutableSharedFlow<List<SongData>>(replay = 1)
        val artists = LocalAlbumArtistRepository(scope = backgroundScope, songDataDao = FakeSongDataDao(songsFlow))

        songsFlow.emit(
            listOf(
                createSongData(album = "Viva la Vida", albumArtist = "Coldplay", track = 1).copy(artists = listOf("Coldplay")),
                createSongData(album = "Graduation", albumArtist = "Kanye West", track = 1).copy(artists = listOf("Kanye West feat. Chris Martin")),
                createSongData(album = "Now 100", track = 1).copy(albumArtist = null, artists = listOf("Adele"), compilation = true),
                createSongData(album = "Now 100", track = 2).copy(albumArtist = null, artists = listOf("Coldplay"), compilation = true),
                createSongData(album = "Watch the Throne", track = 1).copy(albumArtist = null, albumArtists = listOf("Jay-Z", "Kanye West"), artistsTag = listOf("Jay-Z", "Kanye West", "Frank Ocean"))
            )
        )

        artists.getAlbumArtists(AlbumArtistQuery.All()).first().map { it.name } shouldBe listOf("Coldplay", "Jay-Z, Kanye West", "Kanye West", "Various Artists")
        artists.getAlbumArtists(AlbumArtistQuery.Credited()).first().associate { it.name to listOf(it.albumCount, it.appearsOnCount, it.songCount) } shouldBe mapOf(
            "Adele" to listOf(0, 1, 1),
            "Chris Martin" to listOf(0, 1, 1),
            "Coldplay" to listOf(1, 1, 2),
            "Frank Ocean" to listOf(0, 1, 1),
            "Jay-Z" to listOf(0, 1, 1),
            "Jay-Z, Kanye West" to listOf(1, 0, 1),
            "Kanye West" to listOf(1, 1, 2),
            "Various Artists" to listOf(1, 0, 2)
        )
        artists.getAlbumArtists(AlbumArtistQuery.Search("chris")).first().map { it.name } shouldBe listOf("Chris Martin")
    }
}
