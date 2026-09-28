package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveArtistAlbumsTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val albumRepository = FakeAlbumRepository().apply { applyQueryPredicates = true }
    private val observeArtistAlbums = TestMediaActions(songRepository = songRepository, albumRepository = albumRepository).observeArtistAlbums

    private fun key(name: String) = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))

    /** A library: its songs, each holding the identity the rule gives it, and an album for each album they make. */
    private fun library(vararg songs: Song) {
        val identified = songs.toList().withAlbumIdentities()
        songRepository.setSongs(identified)
        albumRepository.setAlbums(
            identified.groupBy { it.albumGroupKey }.map { (key, albumSongs) ->
                val first = albumSongs.first()
                createAlbum(name = first.album!!, albumArtist = first.resolvedAlbumIdentity.albumArtistName, year = first.date?.year, groupKey = key)
            }
        )
    }

    @Test
    fun `an artist's own albums, then the others crediting them, with a compilation among them`() = runTest {
        library(
            createSong(id = 1, album = "Viva la Vida", albumArtist = "Coldplay", artists = listOf("Coldplay"), path = "/m/1.mp3"),
            createSong(id = 2, album = "Graduation", albumArtist = "Kanye West", artists = listOf("Kanye West feat. Chris Martin"), path = "/m/2.mp3"),
            createSong(id = 3, album = "Now 100", albumArtist = "Various Artists", artists = listOf("Coldplay"), path = "/m/3.mp3"),
            createSong(id = 4, album = "Now 100", albumArtist = "Various Artists", artists = listOf("Adele"), path = "/m/4.mp3"),
            createSong(id = 5, album = "Brothers", albumArtist = "The Black Keys", artists = listOf("The Black Keys"), path = "/m/5.mp3"),
        )

        val coldplay = observeArtistAlbums(key("Coldplay")).first()
        coldplay.albums.map { it.name } shouldBe listOf("Viva la Vida")
        coldplay.appearsOn.map { it.name } shouldBe listOf("Now 100")

        val chrisMartin = observeArtistAlbums(key("Chris Martin")).first()
        chrisMartin.albums shouldBe emptyList()
        chrisMartin.appearsOn.map { it.name } shouldBe listOf("Graduation")

        val blackKeys = observeArtistAlbums(key("The Black Keys")).first()
        blackKeys.albums.map { it.name } shouldBe listOf("Brothers")
        blackKeys.appearsOn shouldBe emptyList()
    }

    @Test
    fun `a song without an album name makes no album to appear on`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, album = "", albumArtist = "Kanye West", artists = listOf("Kanye West feat. Chris Martin"))).withAlbumIdentities())

        observeArtistAlbums(key("Chris Martin")).first().appearsOn shouldBe emptyList()
    }
}
