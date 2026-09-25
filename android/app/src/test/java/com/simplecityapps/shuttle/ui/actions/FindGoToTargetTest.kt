package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
    fun `several songs have nowhere to go`() = runTest {
        albumRepository.setAlbums(listOf(album))

        findGoToTarget(MediaSelection.Songs(listOf(createSong(id = 1), createSong(id = 2))), FindGoToTarget.Destination.Album).shouldBeNull()
    }

    @Test
    fun `an album missing from the library is not found`() = runTest {
        findGoToTarget(MediaSelection.Songs(createSong()), FindGoToTarget.Destination.Album).shouldBeNull()
    }
}
