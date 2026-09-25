package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeSongRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeSectionsTest {
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val albumArtists = FakeAlbumArtistRepository()

    private fun homeSections(seed: Long = 42) = HomeSections(albums, albumArtists, songs, seed, Dispatchers.Unconfined)

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `recently played keeps played albums, most recent first`() = runTest {
        val older = createAlbum("Older").copy(lastSongPlayed = now - 2.days)
        val newer = createAlbum("Newer").copy(lastSongPlayed = now - 1.days)
        albums.setAlbums(listOf(older, createAlbum("Never played"), newer))

        homeSections()().first().recentlyPlayed shouldContainExactly listOf(newer, older)
    }

    @Test
    fun `recently added orders albums by their newest song`() = runTest {
        val okComputerSong = createSong(id = 1, album = "OK Computer", albumArtist = "Radiohead").copy(lastModified = now - 3.days)
        val kidASong = createSong(id = 2, album = "Kid A", albumArtist = "Radiohead").copy(lastModified = now - 5.days)
        val kidANewSong = createSong(id = 3, album = "Kid A", albumArtist = "Radiohead").copy(lastModified = now - 1.days)
        val okComputer = createAlbum("OK Computer", "Radiohead", groupKey = okComputerSong.albumGroupKey)
        val kidA = createAlbum("Kid A", "Radiohead", groupKey = kidASong.albumGroupKey)
        songs.setSongs(listOf(okComputerSong, kidASong, kidANewSong))
        albums.setAlbums(listOf(okComputer, kidA))

        homeSections()().first().recentlyAdded shouldContainExactly listOf(kidA, okComputer)
    }

    @Test
    fun `most played keeps albums played at least twice, most first`() = runTest {
        val once = createAlbum("Once", playCount = 1)
        val twice = createAlbum("Twice", playCount = 2)
        val often = createAlbum("Often", playCount = 9)
        albums.setAlbums(listOf(once, twice, often))

        homeSections()().first().mostPlayed shouldContainExactly listOf(often, twice)
    }

    @Test
    fun `something different is the unplayed artists, in the same order for the same seed`() = runTest {
        val unplayed = (1..6).map { createAlbumArtist("Artist $it") }
        albumArtists.setAlbumArtists(unplayed + createAlbumArtist("Played", playCount = 3))

        val first = homeSections(seed = 7)().first().somethingDifferent
        first shouldContainExactlyInAnyOrder unplayed
        homeSections(seed = 7)().first().somethingDifferent shouldBe first
    }

    @Test
    fun `an empty library has empty shelves`() = runTest {
        val sections = homeSections()().first()

        sections.songs.shouldBeEmpty()
        sections.recentlyPlayed.shouldBeEmpty()
        sections.somethingDifferent.shouldBeEmpty()
    }
}
