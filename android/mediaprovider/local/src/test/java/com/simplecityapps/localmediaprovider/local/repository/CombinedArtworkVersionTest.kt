package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class CombinedArtworkVersionTest {
    @Test
    fun `combined version is stable for the same songs in any order`() {
        listOf(createSong("a"), createSong("b")).combinedArtworkVersion() shouldBe
            listOf(createSong("b"), createSong("a")).combinedArtworkVersion()
    }

    @Test
    fun `combined version changes when any song's version changes`() {
        listOf(createSong("a"), createSong("b")).combinedArtworkVersion() shouldNotBe
            listOf(createSong("a"), createSong("c")).combinedArtworkVersion()
    }

    @Test
    fun `combined version is null when no song has a version`() {
        listOf(createSong(null), createSong(null)).combinedArtworkVersion() shouldBe null
    }

    private fun createSong(artworkVersion: String?) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/song.mp3",
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
        channelCount = null,
        artworkVersion = artworkVersion
    )
}
