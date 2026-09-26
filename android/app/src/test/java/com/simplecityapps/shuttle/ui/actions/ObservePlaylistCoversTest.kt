package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePlaylistCoversTest {

    private val playlistRepository = FakePlaylistRepository()
    private val observePlaylistCovers = ObservePlaylistCovers(playlistRepository)

    @Test
    fun `no playlists emits an empty map without querying the repository`() = runTest {
        observePlaylistCovers(emptyList()).first() shouldBe emptyMap()
    }

    @Test
    fun `combines each playlist's cover songs by id`() = runTest {
        val roadTrip = createPlaylist(id = 1, name = "Road trip")
        val gymMix = createPlaylist(id = 2, name = "Gym mix")
        val roadTripSongs = listOf(createSong(id = 1, album = "A"), createSong(id = 2, album = "B"))
        val gymMixSongs = listOf(createSong(id = 3, album = "C"))
        playlistRepository.setSongsForPlaylist(roadTrip, roadTripSongs)
        playlistRepository.setSongsForPlaylist(gymMix, gymMixSongs)

        val covers = observePlaylistCovers(listOf(roadTrip, gymMix)).first()

        covers shouldBe mapOf(roadTrip.id to roadTripSongs, gymMix.id to gymMixSongs)
    }

    @Test
    fun `re-emits as a playlist's songs change`() = runTest {
        val roadTrip = createPlaylist(id = 1, name = "Road trip")
        val firstSongs = listOf(createSong(id = 1, album = "A"))
        playlistRepository.setSongsForPlaylist(roadTrip, firstSongs)

        observePlaylistCovers(listOf(roadTrip)).first()[roadTrip.id] shouldBe firstSongs

        val updatedSongs = listOf(createSong(id = 1, album = "A"), createSong(id = 2, album = "B"))
        playlistRepository.setSongsForPlaylist(roadTrip, updatedSongs)

        observePlaylistCovers(listOf(roadTrip)).first()[roadTrip.id] shouldBe updatedSongs
    }
}
