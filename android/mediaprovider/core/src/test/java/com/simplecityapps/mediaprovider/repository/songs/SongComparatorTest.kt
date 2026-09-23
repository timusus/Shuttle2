package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test

class SongComparatorTest {
    @Test
    fun `defaultComparator sorts a shuffled two-disc album disc by disc, then track`() {
        val songs =
            listOf(
                createSong(name = "D2T2", disc = 2, track = 2),
                createSong(name = "D1T3", disc = 1, track = 3),
                createSong(name = "D2T1", disc = 2, track = 1),
                createSong(name = "D1T1", disc = 1, track = 1),
                createSong(name = "D2T3", disc = 2, track = 3),
                createSong(name = "D1T2", disc = 1, track = 2)
            )

        songs.sortedWith(SongComparator.defaultComparator).map { it.name } shouldBe
            listOf("D1T1", "D1T2", "D1T3", "D2T1", "D2T2", "D2T3")
    }

    @Test
    fun `defaultComparator sorts a single-disc 25-track album numerically, not lexically`() {
        val songs = (1..25).shuffled().map { track -> createSong(name = "T$track", disc = 1, track = track) }

        songs.sortedWith(SongComparator.defaultComparator).map { it.name } shouldBe
            (1..25).map { track -> "T$track" }
    }

    @Test
    fun `defaultComparator treats a null disc as sorting before any numbered disc`() {
        val songs =
            listOf(
                createSong(name = "D1T1", disc = 1, track = 1),
                createSong(name = "NullDisc", disc = null, track = 1)
            )

        songs.sortedWith(SongComparator.defaultComparator).map { it.name } shouldBe
            listOf("NullDisc", "D1T1")
    }

    @Test
    fun `defaultComparator treats a null track as sorting before any numbered track on the same disc`() {
        val songs =
            listOf(
                createSong(name = "D1T1", disc = 1, track = 1),
                createSong(name = "D1TNull", disc = 1, track = null)
            )

        songs.sortedWith(SongComparator.defaultComparator).map { it.name } shouldBe
            listOf("D1TNull", "D1T1")
    }

    private fun createSong(
        name: String,
        disc: Int?,
        track: Int?,
        album: String = "Album",
        albumArtist: String = "Artist"
    ) = Song(
        id = 0,
        name = name,
        albumArtist = albumArtist,
        artists = listOf(albumArtist),
        album = album,
        track = track,
        disc = disc,
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
