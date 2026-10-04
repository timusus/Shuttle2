package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds
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

        val reimported = SongDiff(listOf(imported), listOf(createSong(id = 0, lastModified = serverDate, dateAdded = serverDate).copy(name = "Renamed"))).apply()

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

    @Test
    fun `a re-import updates a song stored without a bit depth to the one the provider now reports`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, bitDepth = null)

        val reimported = SongDiff(listOf(existing), listOf(createSong(id = 0, lastModified = firstImport, bitDepth = 24))).apply()

        reimported.updates.single().run {
            id shouldBe 7
            bitDepth shouldBe 24
        }
    }

    @Test
    fun `a remote lossless song that arrives without a bit depth keeps the stored one`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, bitDepth = 24, audioCodec = "flac")

        val reimported = SongDiff(listOf(existing), listOf(createSong(id = 0, lastModified = firstImport, bitDepth = null, audioCodec = "flac"))).apply()

        reimported.updates shouldBe emptyList()
    }

    @Test
    fun `a remote song whose codec is now lossy clears the stored bit depth`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, bitDepth = 24, audioCodec = "flac")

        val reimported = SongDiff(listOf(existing), listOf(createSong(id = 0, lastModified = firstImport, bitDepth = null, audioCodec = "mp3"))).apply()

        reimported.updates.single().bitDepth shouldBe null
    }

    @Test
    fun `a full listing deletes the songs it no longer holds`() = runTest {
        val kept = createSong(id = 1, lastModified = firstImport, path = "jellyfin://item/1")
        val gone = createSong(id = 2, lastModified = firstImport, path = "jellyfin://item/2")

        val diff = SongDiff(listOf(kept, gone), listOf(kept.copy(id = 0))).apply()

        diff.deletes shouldBe listOf(gone)
    }

    @Test
    fun `a partial listing inserts and updates without deleting what it leaves out`() = runTest {
        val changed = createSong(id = 1, lastModified = firstImport, path = "jellyfin://item/1")
        val untouched = createSong(id = 2, lastModified = firstImport, path = "jellyfin://item/2")
        val added = createSong(id = 0, lastModified = firstImport, path = "jellyfin://item/3")

        val diff = SongDiff(listOf(changed, untouched), listOf(changed.copy(id = 0, name = "Renamed"), added), deleteMissing = false).apply()

        diff.inserts shouldBe listOf(added)
        diff.updates.single().run {
            id shouldBe 1
            name shouldBe "Renamed"
        }
        diff.deletes shouldBe emptyList()
    }

    @Test
    fun `an empty partial listing deletes nothing`() = runTest {
        val stored = createSong(id = 1, lastModified = firstImport)

        SongDiff(listOf(stored), emptyList(), deleteMissing = false).apply().deletes shouldBe emptyList()
    }

    @Test
    fun `a diff sorts songs into inserts - updates - unchanged and deletes by path`() = runTest {
        val unchanged = createSong(id = 1, lastModified = firstImport, path = "a")
        val changed = createSong(id = 2, lastModified = firstImport, path = "b")
        val gone = createSong(id = 3, lastModified = firstImport, path = "c")
        val added = createSong(id = 0, lastModified = firstImport, path = "d")

        val diff = SongDiff(listOf(unchanged, changed, gone), listOf(unchanged.copy(id = 0), changed.copy(id = 0, name = "Renamed"), added)).apply()

        diff.inserts shouldBe listOf(added)
        diff.updates shouldBe listOf(changed.copy(name = "Renamed"))
        diff.deletes shouldBe listOf(gone)
    }

    @Test
    fun `a song differing only below a millisecond is not an update`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport, dateAdded = firstImport)
        val resynced = createSong(id = 0, lastModified = firstImport + 999_999.nanoseconds, dateAdded = firstImport + 1.nanoseconds)

        SongDiff(listOf(stored), listOf(resynced)).apply().updates shouldBe emptyList()
    }

    @Test
    fun `a favourite the server restamped is not an update`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport).copy(favouritedAt = firstImport)
        val resynced = createSong(id = 0, lastModified = firstImport).copy(favouritedAt = Instant.fromEpochSeconds(1_700_000_300))

        SongDiff(listOf(stored), listOf(resynced)).apply().updates shouldBe emptyList()
    }

    @Test
    fun `a favourite the server removed is an update`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport).copy(favouritedAt = firstImport)

        SongDiff(listOf(stored), listOf(createSong(id = 0, lastModified = firstImport))).apply().updates.single().favouritedAt shouldBe null
    }

    @Test
    fun `a song differing only in what an update doesn't write is not an update`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport).copy(playCount = 12, lastPlayed = firstImport, blacklisted = true, bitRate = 320)

        SongDiff(listOf(stored), listOf(createSong(id = 0, lastModified = firstImport))).apply().updates shouldBe emptyList()
    }

    @Test
    fun `a remote song whose server favourite changed is an update`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport)

        val diff = SongDiff(listOf(stored), listOf(createSong(id = 0, lastModified = firstImport).copy(favouritedAt = firstImport))).apply()

        diff.updates.single().favouritedAt shouldBe firstImport
    }

    @Test
    fun `songs sharing a path are one song - the last listed winning`() = runTest {
        val first = createSong(id = 0, lastModified = firstImport, path = "a").copy(name = "First")
        val last = createSong(id = 0, lastModified = firstImport, path = "a").copy(name = "Last")

        SongDiff(emptyList(), listOf(first, last)).apply().inserts shouldBe listOf(last)

        val stored = createSong(id = 7, lastModified = firstImport, path = "a")
        val diff = SongDiff(listOf(stored), listOf(first, last)).apply()
        diff.updates.single().name shouldBe "Last"
        diff.inserts shouldBe emptyList()
    }

    @Test
    fun `a re-import of unchanged songs has nothing to write`() = runTest {
        val stored = (1..50).map { createSong(id = it.toLong(), lastModified = firstImport, path = "p$it") }

        val diff = SongDiff(stored, stored.map { it.copy(id = 0) }).apply()

        diff.inserts shouldBe emptyList()
        diff.updates shouldBe emptyList()
        diff.deletes shouldBe emptyList()
    }

    private fun createSong(
        id: Long,
        lastModified: Instant?,
        path: String = "jellyfin://item/1",
        artworkVersion: String? = null,
        dateAdded: Instant? = null,
        bitDepth: Int? = null,
        audioCodec: String? = null
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
        path = path,
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
        bitDepth = bitDepth,
        sampleRate = null,
        channelCount = null,
        artworkVersion = artworkVersion,
        dateAdded = dateAdded,
        audioCodec = audioCodec
    )
}
