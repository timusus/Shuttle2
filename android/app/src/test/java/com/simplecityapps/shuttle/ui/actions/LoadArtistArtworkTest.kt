package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.withAlbumIdentities
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** An artist row's image by the hero's rule (#823): the same online trust and fallback album as their page's. */
class LoadArtistArtworkTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val albumRepository = FakeAlbumRepository().apply { applyQueryPredicates = true }
    private val loadArtistArtwork = LoadArtistArtwork(TestMediaActions(songRepository = songRepository, albumRepository = albumRepository).observeArtistAlbums, songRepository)

    private val mbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    private fun artist(name: String) = createAlbumArtist(name = name, groupKey = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name)))

    private fun library(vararg songs: Song) {
        val identified = songs.toList().withAlbumIdentities()
        songRepository.setSongs(identified)
        albumRepository.setAlbums(
            identified.groupBy { it.albumGroupKey }.map { (key, albumSongs) ->
                val first = albumSongs.first()
                createAlbum(name = first.album!!, albumArtist = first.resolvedAlbumIdentity.albumArtistName, year = first.date?.year, playCount = albumSongs.sumOf { it.playCount }, groupKey = key)
            }
        )
    }

    @Test
    fun `an artist whose tags carry one MusicBrainz id may use the online image, falling back to their most played album`() = runTest {
        library(
            createSong(id = 1, album = "OK Computer", albumArtist = "Radiohead", artists = listOf("Radiohead"), path = "/m/1.mp3", playCount = 9).copy(mbAlbumArtistIds = listOf(mbid)),
            createSong(id = 2, album = "Kid A", albumArtist = "Radiohead", artists = listOf("Radiohead"), path = "/m/2.mp3", playCount = 1).copy(mbAlbumArtistIds = listOf(mbid)),
        )

        val artwork = loadArtistArtwork(artist("Radiohead"))

        artwork.onlineLookup shouldBe true
        artwork.fallbackAlbum?.name shouldBe "OK Computer"
    }

    @Test
    fun `an artist without a MusicBrainz id never uses the online image`() = runTest {
        library(createSong(id = 1, album = "OK Computer", albumArtist = "Radiohead", artists = listOf("Radiohead"), path = "/m/1.mp3"))

        loadArtistArtwork(artist("Radiohead")).onlineLookup shouldBe false
    }

    @Test
    fun `a credited-only artist falls back to an album they appear on`() = runTest {
        library(createSong(id = 1, album = "Graduation", albumArtist = "Kanye West", artists = listOf("Kanye West feat. Chris Martin"), path = "/m/1.mp3"))

        loadArtistArtwork(artist("Chris Martin")).fallbackAlbum?.name shouldBe "Graduation"
    }
}
