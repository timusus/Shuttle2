package com.simplecityapps.localmediaprovider.local.provider.mediastore

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaStoreGenresTest {
    @Test
    fun `each song gets the genres MediaStore lists it under, after its own`() {
        val songs = listOf(song("1", genres = listOf("Read")), song("2"), song("3"))

        val withGenres = songs.withGenres(mapOf("1" to listOf("Rock", "Pop"), "3" to listOf("Jazz")))

        withGenres.map { it.genres } shouldBe listOf(listOf("Read", "Rock", "Pop"), emptyList(), listOf("Jazz"))
    }

    @Test
    fun `a song with no MediaStore id gets no genres`() {
        val song = song(null)

        listOf(song).withGenres(mapOf("1" to listOf("Rock"))) shouldBe listOf(song)
    }

    private fun song(
        externalId: String?,
        genres: List<String> = emptyList()
    ) = Song(
        id = 0,
        name = "Song $externalId",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 0,
        date = null,
        genres = genres,
        path = "/music/$externalId.mp3",
        size = 1,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = MediaProviderType.MediaStore,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
