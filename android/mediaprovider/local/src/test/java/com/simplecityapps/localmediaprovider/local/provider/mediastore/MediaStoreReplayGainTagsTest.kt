package com.simplecityapps.localmediaprovider.local.provider.mediastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
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
    fun `a reader that throws leaves ReplayGain values null`() {
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ -> throw IllegalStateException("corrupt file") }

        val result = runBlocking { createMediaStoreSong().withReplayGainTags(reader) }

        result.replayGainTrack shouldBe null
        result.replayGainAlbum shouldBe null
    }

    @Test
    fun `one unreadable file doesn't stop the other songs importing`() {
        val reader = MediaStoreReplayGainReader { uri, _, _, _, _ ->
            if (uri.lastPathSegment == "2") throw IllegalArgumentException("corrupt file")
            ReplayGainTags(track = -3.5, album = -4.2)
        }
        val songs = (1..3).map { id -> createMediaStoreSong(externalId = "$id", path = "/music/$id.mp3") }

        val result = runBlocking { songs.withReplayGainTags(emptyList(), reader, readUnchanged = false).toList() }

        result.sortedBy { it.path }.map { it.replayGainTrack } shouldBe listOf(-3.5, null, -3.5)
    }

    @Test
    fun `an unchanged file keeps its stored values without being read`() {
        var readCount = 0
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ ->
            readCount++
            ReplayGainTags(1.0, 1.0)
        }
        val existing = createMediaStoreSong(replayGainTrack = -3.5, replayGainAlbum = -4.2)

        val result = runBlocking { listOf(createMediaStoreSong()).withReplayGainTags(listOf(existing), reader, readUnchanged = false).toList() }

        readCount shouldBe 0
        result.single().replayGainTrack shouldBe -3.5
        result.single().replayGainAlbum shouldBe -4.2
    }

    @Test
    fun `a new, modified or resized file is read`() {
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ -> ReplayGainTags(track = -1.0, album = -2.0) }
        val existing =
            listOf(
                createMediaStoreSong(path = "/music/modified.mp3", replayGainTrack = -9.0),
                createMediaStoreSong(path = "/music/resized.mp3", replayGainTrack = -9.0)
            )
        val songs =
            listOf(
                createMediaStoreSong(path = "/music/new.mp3"),
                createMediaStoreSong(path = "/music/modified.mp3", lastModified = Instant.fromEpochMilliseconds(1000)),
                createMediaStoreSong(path = "/music/resized.mp3", size = 2048L)
            )

        val result = runBlocking { songs.withReplayGainTags(existing, reader, readUnchanged = false).toList() }

        result.map { it.replayGainTrack } shouldBe listOf(-1.0, -1.0, -1.0)
    }

    @Test
    fun `the backfill reads unchanged files too`() {
        val reader = MediaStoreReplayGainReader { _, _, _, _, _ -> ReplayGainTags(track = -3.5, album = -4.2) }
        val existing = createMediaStoreSong()

        val result = runBlocking { listOf(createMediaStoreSong()).withReplayGainTags(listOf(existing), reader, readUnchanged = true).toList() }

        result.single().replayGainTrack shouldBe -3.5
        result.single().replayGainAlbum shouldBe -4.2
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

    private fun createMediaStoreSong(
        externalId: String? = "42",
        path: String = "/storage/emulated/0/Music/test.mp3",
        size: Long = 1024L,
        lastModified: Instant = Instant.fromEpochMilliseconds(0),
        replayGainTrack: Double? = null,
        replayGainAlbum: Double? = null
    ) = Song(
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
        path = path,
        size = size,
        mimeType = "audio/mpeg",
        lastModified = lastModified,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = MediaProviderType.MediaStore,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = null
    )
}
