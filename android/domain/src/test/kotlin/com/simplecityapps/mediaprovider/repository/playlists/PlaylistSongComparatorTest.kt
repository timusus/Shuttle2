package com.simplecityapps.mediaprovider.repository.playlists

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlaylistSongComparatorTest {
    @Test
    fun `Position comparator sorts by playlist position ascending`() {
        val songs = listOf(
            createPlaylistSong(name = "Third", position = 2),
            createPlaylistSong(name = "First", position = 0),
            createPlaylistSong(name = "Second", position = 1)
        )

        songs.sortedWith(PlaylistSongSortOrder.Position.comparator).map { it.song.name } shouldBe
            listOf("First", "Second", "Third")
    }

    @Test
    fun `Position comparator reversed sorts by playlist position descending`() {
        val songs = listOf(
            createPlaylistSong(name = "Third", position = 2),
            createPlaylistSong(name = "First", position = 0),
            createPlaylistSong(name = "Second", position = 1)
        )

        songs.sortedWith(PlaylistSongSortOrder.Position.comparator.reversed()).map { it.song.name } shouldBe
            listOf("Third", "Second", "First")
    }

    @Test
    fun `SongName comparator reversed sorts alphabetically descending`() {
        val songs = listOf(
            createPlaylistSong(name = "Bravo", position = 0),
            createPlaylistSong(name = "Alpha", position = 1),
            createPlaylistSong(name = "Charlie", position = 2)
        )

        songs.sortedWith(PlaylistSongSortOrder.SongName.comparator.reversed()).map { it.song.name } shouldBe
            listOf("Charlie", "Bravo", "Alpha")
    }

    private fun createPlaylistSong(
        name: String,
        position: Int
    ) = PlaylistSong(
        id = position.toLong(),
        sortOrder = position.toLong(),
        song = createSong(name = name)
    )

    private fun createSong(name: String) = Song(
        id = 0,
        name = name,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
