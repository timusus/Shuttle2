package com.simplecityapps.localmediaprovider.local.provider.taglib

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class TaglibIncrementalScanTest {
    @Test
    fun `an unchanged file emits its stored song, keeping its id, play count and artwork version`() {
        val stored = storedSong()
        val file = audioFile()

        file.unchangedSong(mapOf(stored.path to stored)) shouldBe stored
    }

    @Test
    fun `a file with no stored song is parsed`() {
        audioFile().unchangedSong(emptyMap()) shouldBe null
    }

    @Test
    fun `a file with another path is parsed`() {
        val stored = storedSong()

        audioFile(path = "/storage/emulated/0/Music/other.mp3").unchangedSong(mapOf(stored.path to stored)) shouldBe null
    }

    @Test
    fun `a modified file is parsed`() {
        val stored = storedSong()

        audioFile(lastModified = stored.lastModified.epochSeconds * 1000 + 60_000)
            .unchangedSong(mapOf(stored.path to stored)) shouldBe null
    }

    @Test
    fun `a resized file is parsed`() {
        val stored = storedSong()

        audioFile(size = stored.size + 1).unchangedSong(mapOf(stored.path to stored)) shouldBe null
    }

    @Test
    fun `a file whose duration changed is parsed`() {
        val stored = storedSong()

        // Same second-granularity date and size, but a different song: the duration catches the swap
        audioFile(duration = stored.duration.toLong() + 30_000).unchangedSong(mapOf(stored.path to stored)) shouldBe null
    }

    @Test
    fun `a duration within MediaStore rounding of the stored one still matches`() {
        val stored = storedSong()

        audioFile(duration = stored.duration.toLong() + 500).unchangedSong(mapOf(stored.path to stored)) shouldBe stored
    }

    @Test
    fun `a file MediaStore couldn't read a duration for matches on date and size`() {
        val stored = storedSong()

        audioFile(duration = null).unchangedSong(mapOf(stored.path to stored)) shouldBe stored
    }

    @Test
    fun `a stored song with no modified date is parsed`() {
        val stored = storedSong(lastModified = null)

        audioFile().unchangedSong(mapOf(stored.path to stored)) shouldBe null
    }

    private fun audioFile(
        path: String = "/storage/emulated/0/Music/Album/01 Song.flac",
        size: Long = 2_048L,
        lastModified: Long = 1_700_000_000_000L,
        duration: Long? = 185_000L
    ) = MediaStoreAudioFile(
        id = 7,
        path = path,
        displayName = "01 Song.flac",
        size = size,
        lastModified = lastModified,
        mimeType = "audio/flac",
        duration = duration
    )

    private fun storedSong(lastModified: Instant? = Instant.fromEpochMilliseconds(1_700_000_000_000L)) = Song(
        id = 42,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 185_000,
        date = null,
        genres = emptyList(),
        path = "/storage/emulated/0/Music/Album/01 Song.flac",
        size = 2_048L,
        mimeType = "audio/flac",
        lastModified = lastModified,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 17,
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
        channelCount = null,
        artworkVersion = "artwork-v1"
    )
}
