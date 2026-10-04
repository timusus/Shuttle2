package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class LocalFileTagMergerTest {
    private val existing = song(path = "/music/a.mp3", size = 1024, lastModified = 1_700_000_000_000)

    @Test
    fun `an unchanged file reuses its stored song`() {
        LocalFileTagMerger(listOf(existing)).unchangedSong("/music/a.mp3", 1024, 1_700_000_000_000) shouldBe existing
    }

    @Test
    fun `a file with a different size is read again`() {
        LocalFileTagMerger(listOf(existing)).unchangedSong("/music/a.mp3", 2048, 1_700_000_000_000) shouldBe null
    }

    @Test
    fun `a file with a different modified date is read again`() {
        LocalFileTagMerger(listOf(existing)).unchangedSong("/music/a.mp3", 1024, 1_700_000_001_000) shouldBe null
    }

    @Test
    fun `modified dates compare in milliseconds, not seconds`() {
        // A source that reported seconds (1_700_000_000) would never match the stored milliseconds
        LocalFileTagMerger(listOf(existing)).unchangedSong("/music/a.mp3", 1024, 1_700_000_000) shouldBe null
    }

    @Test
    fun `a file with no stored song is read`() {
        LocalFileTagMerger(listOf(existing)).unchangedSong("/music/new.mp3", 1024, 1_700_000_000_000) shouldBe null
    }

    @Test
    fun `a stored song with no modified date is read again`() {
        LocalFileTagMerger(listOf(existing.copy(lastModified = null))).unchangedSong("/music/a.mp3", 1024, 0) shouldBe null
    }

    @Test
    fun `a stored song whose file is gone is never returned`() {
        // The importer's diff removes songs a scan doesn't list; the merger only answers for files the scan found
        val merger = LocalFileTagMerger(listOf(existing, song(path = "/music/gone.mp3", size = 1, lastModified = 1)))

        merger.unchangedSong("/music/a.mp3", 1024, 1_700_000_000_000) shouldBe existing
    }

    @Test
    fun `reading unchanged files forces a read`() {
        LocalFileTagMerger(listOf(existing), readUnchanged = true).unchangedSong("/music/a.mp3", 1024, 1_700_000_000_000) shouldBe null
    }

    private fun song(
        path: String,
        size: Long,
        lastModified: Long
    ) = Song(
        id = 7,
        name = "Stored",
        albumArtist = null,
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 1000,
        date = null,
        genres = emptyList(),
        path = path,
        size = size,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(lastModified),
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
