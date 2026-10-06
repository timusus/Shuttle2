package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NowPlayingArtworkTest {
    @Test
    fun `with the setting off the model is the song itself`() {
        val song = song(albumArtist = "Radiohead")

        nowPlayingArtworkModel(song, artistImage = false) shouldBe song
    }

    @Test
    fun `an artist image is the album artist with the song for its fallback`() {
        val song = song(albumArtist = "Radiohead")

        val model = nowPlayingArtworkModel(song, artistImage = true) as ArtistImageArtwork

        model.artist.name shouldBe "Radiohead"
        model.artist.groupKey shouldBe song.albumArtistGroupKey
        model.song shouldBe song
    }

    @Test
    fun `a song with no album artist has no artist to picture so shows itself`() {
        song(albumArtist = null).let { nowPlayingArtworkModel(it, artistImage = true) shouldBe it }
        song(albumArtist = " ").let { nowPlayingArtworkModel(it, artistImage = true) shouldBe it }
    }

    private fun song(albumArtist: String?) = Song(
        id = 1,
        name = "Airbag",
        albumArtist = albumArtist,
        artists = listOf("Radiohead"),
        album = "OK Computer",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/airbag.mp3",
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
    )
}
