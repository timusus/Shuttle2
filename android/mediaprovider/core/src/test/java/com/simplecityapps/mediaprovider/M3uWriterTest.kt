package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.ByteArrayInputStream
import org.junit.Test

class M3uWriterTest {
    private val m3uWriter = M3uWriter()

    @Test
    fun `writes the extm3u header`() {
        m3uWriter.write(emptyList()) shouldStartWith "#EXTM3U"
    }

    @Test
    fun `writes an extinf line and path for each song`() {
        val song = createSong(name = "Test Song", artist = "Test Artist", duration = 180_000, path = "/music/test.mp3")

        val result = m3uWriter.write(listOf(song))

        result shouldContain "#EXTINF:180, Test Artist - Test Song"
        result shouldContain "/music/test.mp3"
    }

    @Test
    fun `falls back to unknown artist and track when missing`() {
        val song = createSong(name = null, artist = null, duration = 180_000, path = "/music/test.mp3")

        m3uWriter.write(listOf(song)) shouldContain "Unknown Artist - Unknown Track"
    }

    @Test
    fun `duration is truncated from milliseconds to whole seconds`() {
        val song = createSong(name = "Test", artist = "Test", duration = 125_500, path = "/test.mp3")

        m3uWriter.write(listOf(song)) shouldContain "#EXTINF:125,"
    }

    @Test
    fun `output round-trips through M3uParser`() {
        val songs = listOf(
            createSong(name = "Song 1", artist = "Artist 1", duration = 123_000, path = "/music/song1.mp3"),
            createSong(name = "Song 2", artist = "Artist 2", duration = 321_000, path = "/music/song2.mp3")
        )

        val content = m3uWriter.write(songs)
        val parsed = M3uParser().parse(
            path = "/music/playlist.m3u",
            fileName = "playlist.m3u",
            inputStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        )

        parsed.entries.map { it.location } shouldBe listOf("/music/song1.mp3", "/music/song2.mp3")
    }

    @Test
    fun `preserved entries anchored to a repeated song are written once`() {
        val song = createSong(name = "Test Song", artist = "Test Artist", duration = 180_000, path = "/music/test.mp3")
        val unresolved = Entry(location = "/moved/gone.mp3", duration = null, artist = null, track = null, rawLines = listOf("/moved/gone.mp3"))

        val result = m3uWriter.write(listOf(song, song), preservedEntries = mapOf(song.id to listOf(unresolved)))

        result.split("/moved/gone.mp3").size - 1 shouldBe 1
    }

    private fun createSong(
        name: String?,
        artist: String?,
        duration: Int,
        path: String
    ) = Song(
        id = 1,
        name = name,
        albumArtist = artist,
        artists = artist?.let { listOf(it) } ?: emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = null,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
