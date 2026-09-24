package com.simplecityapps.localmediaprovider.local.provider.mediastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreReplayGainTagsTest {
    @Test
    fun `song gets ReplayGain values from the tag reader`() {
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ -> ReplayGainTags(track = -3.5, album = -4.2) }

        val result = runBlocking { createMediaStoreSong().withReplayGainTags(reader) }

        result.replayGainTrack shouldBe -3.5
        result.replayGainAlbum shouldBe -4.2
    }

    @Test
    fun `a failing read leaves ReplayGain values null`() {
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ -> null }

        val result = runBlocking { createMediaStoreSong().withReplayGainTags(reader) }

        result.replayGainTrack shouldBe null
        result.replayGainAlbum shouldBe null
    }

    @Test
    fun `a song without a MediaStore id is left untouched`() {
        var readCalled = false
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ ->
            readCalled = true
            ReplayGainTags(1.0, 1.0)
        }
        val song = createMediaStoreSong(externalId = null)

        val result = runBlocking { song.withReplayGainTags(reader) }

        result shouldBe song
        readCalled shouldBe false
    }

    private fun createMediaStoreSong(externalId: String? = "42") = Song(
        id = 0,
        name = "Test Song",
        albumArtist = "Test Album Artist",
        artists = listOf("Test Artist"),
        album = "Test Album",
        track = 1,
        disc = 1,
        duration = 1000,
        date = null,
        genres = emptyList(),
        path = "/storage/emulated/0/Music/test.mp3",
        size = 1024L,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(0),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = MediaProviderType.MediaStore,
        replayGainTrack = null,
        replayGainAlbum = null,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = null
    )
}
