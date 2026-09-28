package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SongDiffTest {
    private val firstImport = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `an update takes the new song's data and keeps the existing id`() {
        val existing = createSong(id = 7, lastModified = firstImport, artworkVersion = "v1")
        val new = createSong(id = 0, lastModified = Instant.fromEpochSeconds(1_800_000_000), artworkVersion = "v2")

        SongDiff(listOf(existing), listOf(new)).update(existing, new) shouldBe new.copy(id = 7)
    }

    @Test
    fun `an update without a date keeps the date from the first import`() {
        val existing = createSong(id = 7, lastModified = firstImport)
        val new = createSong(id = 0, lastModified = null)

        SongDiff(listOf(existing), listOf(new)).update(existing, new).lastModified shouldBe firstImport
    }

    @Test
    fun `a remote song's server date replaces the stamp from an older import`() {
        val serverDate = Instant.fromEpochSeconds(1_600_000_000)
        val existing = createSong(id = 7, lastModified = serverDate, dateAdded = firstImport)
        val new = createSong(id = 0, lastModified = serverDate, dateAdded = serverDate)

        SongDiff(listOf(existing), listOf(new)).update(existing, new).dateAdded shouldBe serverDate
    }

    @Test
    fun `a remote song keeps its server date across re-imports`() = runTest {
        val serverDate = Instant.fromEpochSeconds(1_600_000_000)
        val imported = SongDiff(emptyList(), listOf(createSong(id = 0, lastModified = serverDate, dateAdded = serverDate))).apply()
            .inserts.single().copy(id = 7)

        val reimported = SongDiff(listOf(imported), listOf(createSong(id = 0, lastModified = serverDate, dateAdded = serverDate))).apply()

        reimported.updates.single().run {
            id shouldBe 7
            dateAdded shouldBe serverDate
        }
    }

    @Test
    fun `a song from a provider with no date added keeps the one stamped when it first reached the library`() {
        val existing = createSong(id = 7, lastModified = firstImport, dateAdded = firstImport)
        val retagged = createSong(id = 0, lastModified = Instant.fromEpochSeconds(1_800_000_000), dateAdded = null)

        SongDiff(listOf(existing), listOf(retagged)).update(existing, retagged).dateAdded shouldBe firstImport
    }

    private fun createSong(
        id: Long,
        lastModified: Instant?,
        artworkVersion: String? = null,
        dateAdded: Instant? = null
    ) = Song(
        id = id,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "jellyfin://item/1",
        size = 0,
        mimeType = "Audio/*",
        lastModified = lastModified,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = artworkVersion,
        dateAdded = dateAdded
    )
}
