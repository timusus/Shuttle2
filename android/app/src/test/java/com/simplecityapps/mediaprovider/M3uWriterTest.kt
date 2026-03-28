package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uWriterTest {
    private val m3uWriter = M3uWriter()

    @Test
    fun testWriteSimpleSong() {
        val song = createTestSong(
            name = "Test Song",
            artist = "Test Artist",
            duration = 180000, // 3 minutes in milliseconds
            path = "/music/test.mp3"
        )

        val result = m3uWriter.write(listOf(song))

        assertNotNull(result)
        assertTrue(result.startsWith("#EXTM3U"))
        assertTrue(result.contains("#EXTINF:180, Test Artist - Test Song"))
        assertTrue(result.contains("/music/test.mp3"))
    }

    @Test
    fun testWriteMultipleSongs() {
        val songs = listOf(
            createTestSong(
                name = "Song 1",
                artist = "Artist 1",
                duration = 123000,
                path = "/music/song1.mp3"
            ),
            createTestSong(
                name = "Song 2",
                artist = "Artist 2",
                duration = 321000,
                path = "/music/song2.mp3"
            )
        )

        val result = m3uWriter.write(songs)

        assertNotNull(result)
        assertTrue(result.contains("#EXTINF:123, Artist 1 - Song 1"))
        assertTrue(result.contains("/music/song1.mp3"))
        assertTrue(result.contains("#EXTINF:321, Artist 2 - Song 2"))
        assertTrue(result.contains("/music/song2.mp3"))
    }

    @Test
    fun testWriteWithNullSongName() {
        val song = createTestSong(
            name = null,
            artist = "Test Artist",
            duration = 180000,
            path = "/music/test.mp3"
        )

        val result = m3uWriter.write(listOf(song))

        assertNotNull(result)
        assertTrue(result.contains("Test Artist - Unknown Track"))
    }

    @Test
    fun testWriteWithNullArtist() {
        val song = createTestSong(
            name = "Test Song",
            artist = null,
            duration = 180000,
            path = "/music/test.mp3"
        )

        val result = m3uWriter.write(listOf(song))

        assertNotNull(result)
        assertTrue(result.contains("Unknown Artist - Test Song"))
    }

    @Test
    fun testWriteWithCustomPathResolver() {
        val song = createTestSong(
            name = "Test Song",
            artist = "Test Artist",
            duration = 180000,
            path = "content://media/external/audio/media/123"
        )

        // Custom resolver that converts content URI to a file path
        val customResolver: (Song) -> String? = { "/sdcard/Music/test.mp3" }

        val result = m3uWriter.write(listOf(song), customResolver)

        assertNotNull(result)
        assertTrue(result.contains("/sdcard/Music/test.mp3"))
    }

    @Test
    fun testWriteWithNullPathResolver() {
        val song = createTestSong(
            name = "Test Song",
            artist = "Test Artist",
            duration = 180000,
            path = "/music/test.mp3"
        )

        // Resolver that returns null
        val nullResolver: (Song) -> String? = { null }

        val result = m3uWriter.write(listOf(song), nullResolver)

        // Should return null when no valid songs found
        assertNull(result)
    }

    @Test
    fun testWriteEmptyList() {
        val result = m3uWriter.write(emptyList())

        assertEquals("", result)
    }

    @Test
    fun testWriteWithMixedValidAndInvalidSongs() {
        val songs = listOf(
            createTestSong(
                name = "Valid Song",
                artist = "Artist",
                duration = 180000,
                path = "/music/valid.mp3"
            ),
            createTestSong(
                name = "Invalid Song",
                artist = "Artist",
                duration = 180000,
                path = "content://invalid"
            )
        )

        // Resolver that returns null for content URIs
        val resolver: (Song) -> String? = { song ->
            if (song.path.startsWith("content://")) null else song.path
        }

        val result = m3uWriter.write(songs, resolver)

        assertNotNull(result)
        assertTrue(result.contains("Valid Song"))
        assertTrue(!result.contains("Invalid Song"))
    }

    @Test
    fun testDurationConversion() {
        // Duration is in milliseconds in Song model, but should be in seconds in m3u
        val song = createTestSong(
            name = "Test",
            artist = "Test",
            duration = 125500, // 125.5 seconds in milliseconds
            path = "/test.mp3"
        )

        val result = m3uWriter.write(listOf(song))

        assertNotNull(result)
        // Should be truncated to 125 seconds (integer division)
        assertTrue(result.contains("#EXTINF:125,"))
    }

    @Test
    fun testFormatStructure() {
        val song = createTestSong(
            name = "Test Song",
            artist = "Test Artist",
            duration = 180000,
            path = "/music/test.mp3"
        )

        val result = m3uWriter.write(listOf(song))

        assertNotNull(result)

        val lines = result.lines()
        assertEquals("#EXTM3U", lines[0])
        assertTrue(lines[1].isEmpty()) // Empty line after header
        assertTrue(lines[2].startsWith("#EXTINF:"))
        assertEquals("/music/test.mp3", lines[3])
    }

    // Helper function to create test songs
    private fun createTestSong(
        name: String?,
        artist: String?,
        duration: Int,
        path: String
    ): Song = Song(
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
        replayGainTrack = null,
        replayGainAlbum = null,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
