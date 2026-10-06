package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class FindGoToTargetTest {

    private val albumRepository = FakeAlbumRepository()
    private val albumArtistRepository = FakeAlbumArtistRepository()
    private val findGoToTarget = TestMediaActions(albumRepository = albumRepository, albumArtistRepository = albumArtistRepository).findGoToTarget

    private val album = createAlbum()
    private val artist = createAlbumArtist()

    @Test
    fun `a single song goes to its album`() = runTest {
        albumRepository.setAlbums(listOf(album))

        findGoToTarget(MediaSelection.Songs(createSong()), FindGoToTarget.Destination.Album) shouldBe NavigationTarget.Album(album)
    }

    @Test
    fun `a single song or album goes to its artist`() = runTest {
        albumArtistRepository.setAlbumArtists(listOf(artist))

        findGoToTarget(MediaSelection.Songs(createSong()), FindGoToTarget.Destination.AlbumArtist) shouldBe NavigationTarget.AlbumArtist(artist)
        findGoToTarget(MediaSelection.Albums(album), FindGoToTarget.Destination.AlbumArtist) shouldBe NavigationTarget.AlbumArtist(artist)
    }

    @Test
    fun `a song or album of several album artists or one featuring another goes to its primary artist`() = runTest {
        val radiohead = createAlbumArtist("Radiohead", groupKey = AlbumArtistGroupKey("radiohead"))
        albumArtistRepository.applyQueryPredicates = true
        albumArtistRepository.setAlbumArtists(
            listOf(radiohead, createAlbumArtist("Thom Yorke", groupKey = AlbumArtistGroupKey("thom yorke")), createAlbumArtist("Björk", groupKey = AlbumArtistGroupKey("björk")))
        )

        findGoToTarget(MediaSelection.Songs(createSong(albumArtist = "Radiohead feat. Björk")), FindGoToTarget.Destination.AlbumArtist) shouldBe NavigationTarget.AlbumArtist(radiohead)
        findGoToTarget(MediaSelection.Songs(createSong(albumArtist = "", albumArtists = listOf("Radiohead", "Thom Yorke"))), FindGoToTarget.Destination.AlbumArtist) shouldBe
            NavigationTarget.AlbumArtist(radiohead)
        val album = createAlbum(albumArtist = "Radiohead, Thom Yorke").copy(albumArtistKeys = listOf(radiohead.groupKey, AlbumArtistGroupKey("thom yorke")))
        findGoToTarget(MediaSelection.Albums(album), FindGoToTarget.Destination.AlbumArtist) shouldBe NavigationTarget.AlbumArtist(radiohead)
    }

    @Test
    fun `several songs have nowhere to go`() = runTest {
        albumRepository.setAlbums(listOf(album))

        findGoToTarget(MediaSelection.Songs(listOf(createSong(id = 1), createSong(id = 2))), FindGoToTarget.Destination.Album).shouldBeNull()
    }

    @Test
    fun `an album missing from the library is not found`() = runTest {
        findGoToTarget(MediaSelection.Songs(createSong()), FindGoToTarget.Destination.Album).shouldBeNull()
    }
}
