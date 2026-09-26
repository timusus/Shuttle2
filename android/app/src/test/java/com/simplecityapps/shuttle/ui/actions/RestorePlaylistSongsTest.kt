package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.shuttle.model.PlaylistSong
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestorePlaylistSongsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val restorePlaylistSongs = RestorePlaylistSongs(playlistRepository)
    private val playlist = createPlaylist(id = 1L)

    private val song = createSong(id = 1, name = "Song")
    private val other = createSong(id = 2, name = "Other")
    private val third = createSong(id = 3, name = "Third")

    @Test
    fun `re-adds the removed songs and restores their original order`() = runTest {
        val before = listOf(
            PlaylistSong(id = 0, sortOrder = 0, song = song),
            PlaylistSong(id = 1, sortOrder = 1, song = other),
            PlaylistSong(id = 2, sortOrder = 2, song = third),
        )
        val removed = listOf(before[1])
        // The repository appends the re-added song, so it currently reads as song, third, other.
        playlistRepository.setSongsForPlaylist(playlist, listOf(song, third))

        restorePlaylistSongs(playlist, removed, before)

        playlistRepository.addedToPlaylist shouldBe listOf(playlist to listOf(other))
        playlistRepository.reorderedSongs!!.map { it.song to it.sortOrder } shouldBe listOf(song to 0L, other to 1L, third to 2L)
    }

    @Test
    fun `a song added since the removal keeps its place at the end`() = runTest {
        val before = listOf(
            PlaylistSong(id = 0, sortOrder = 0, song = song),
            PlaylistSong(id = 1, sortOrder = 1, song = other),
        )
        val removed = listOf(before[1])
        // "third" was added to the playlist after the removal, so it has no recorded position.
        playlistRepository.setSongsForPlaylist(playlist, listOf(song, third))

        restorePlaylistSongs(playlist, removed, before)

        playlistRepository.reorderedSongs!!.map { it.song to it.sortOrder } shouldBe listOf(song to 0L, other to 1L, third to 2L)
    }
}
