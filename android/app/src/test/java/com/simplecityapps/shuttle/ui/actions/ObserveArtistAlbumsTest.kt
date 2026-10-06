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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveArtistAlbumsTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val albumRepository = FakeAlbumRepository().apply { applyQueryPredicates = true }
    private val observeArtistAlbums = TestMediaActions(songRepository = songRepository, albumRepository = albumRepository).observeArtistAlbums

    private fun key(name: String) = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))

    /** The artist's albums once Appears On has loaded too; it follows the albums, which emit first. */
    private suspend fun TestScope.settled(key: AlbumArtistGroupKey): ArtistAlbums {
        var latest: ArtistAlbums? = null
        observeArtistAlbums(key).onEach { latest = it }.launchIn(backgroundScope)
        runCurrent()
        return latest!!
    }

    /** A library: its songs, each holding the identity the rule gives it, and an album for each album they make. */
    private fun library(vararg songs: Song) {
        val identified = songs.toList().withAlbumIdentities()
        songRepository.setSongs(identified)
        albumRepository.setAlbums(
            identified.groupBy { it.albumGroupKey }.map { (key, albumSongs) ->
                val first = albumSongs.first()
                createAlbum(name = first.album!!, albumArtist = first.resolvedAlbumIdentity.albumArtistName, year = first.date?.year, groupKey = key)
                    .copy(albumArtistKeys = first.resolvedAlbumIdentity.albumArtistKeys)
            }
        )
    }

    @Test
    fun `an album with several album artists is each one's own - and one featured by its album artist is on their Appears On`() = runTest {
        library(
            createSong(id = 1, album = "Watch the Throne", albumArtist = "", artists = listOf("Jay-Z"), path = "/m/1.mp3").copy(albumArtist = null, albumArtists = listOf("Jay-Z", "Kanye West")),
            createSong(id = 2, album = "Motion", albumArtist = "Calvin Harris feat. Rihanna", artists = listOf("Calvin Harris"), path = "/m/2.mp3"),
        )

        settled(key("Jay-Z")).albums.map { it.name } shouldBe listOf("Watch the Throne")
        settled(key("Kanye West")).albums.map { it.name } shouldBe listOf("Watch the Throne")
        settled(key("Calvin Harris")).albums.map { it.name } shouldBe listOf("Motion")

        val rihanna = settled(key("Rihanna"))
        rihanna.albums shouldBe emptyList()
        rihanna.appearsOn.map { it.name } shouldBe listOf("Motion")
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

        val coldplay = settled(key("Coldplay"))
        coldplay.albums.map { it.name } shouldBe listOf("Viva la Vida")
        coldplay.appearsOn.map { it.name } shouldBe listOf("Now 100")

        val chrisMartin = settled(key("Chris Martin"))
        chrisMartin.albums shouldBe emptyList()
        chrisMartin.appearsOn.map { it.name } shouldBe listOf("Graduation")

        val blackKeys = settled(key("The Black Keys"))
        blackKeys.albums.map { it.name } shouldBe listOf("Brothers")
        blackKeys.appearsOn shouldBe emptyList()
    }

    @Test
    fun `the albums emit before Appears On has loaded`() = runTest {
        library(createSong(id = 1, album = "Viva la Vida", albumArtist = "Coldplay", artists = listOf("Coldplay"), path = "/m/1.mp3"))

        observeArtistAlbums(key("Coldplay")).first().albums.map { it.name } shouldBe listOf("Viva la Vida")
    }

    @Test
    fun `a song without an album name makes no album to appear on`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, album = "", albumArtist = "Kanye West", artists = listOf("Kanye West feat. Chris Martin"))).withAlbumIdentities())

        settled(key("Chris Martin")).appearsOn shouldBe emptyList()
    }
}
