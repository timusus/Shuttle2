package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
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
    fun `lyrics alone are not a change as the stored songs are read without them`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport)
        val scanned = createSong(id = 0, lastModified = firstImport).copy(lyrics = "la la la")

        SongDiff(listOf(existing), listOf(scanned)).apply().updates shouldBe emptyList()
    }

    @Test
    fun `an update carries the scanned lyrics to be stored`() {
        val existing = createSong(id = 7, lastModified = firstImport)
        val scanned = createSong(id = 0, lastModified = Instant.fromEpochSeconds(1_800_000_000)).copy(lyrics = "la la la")

        SongDiff(listOf(existing), listOf(scanned)).update(existing, scanned).lyrics shouldBe "la la la"
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
    fun `a re-import fills in the format of a song stored without one`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport)
        val resynced = createSong(id = 0, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(sampleRate = 96000, channelCount = 2)

        SongDiff(listOf(existing), listOf(resynced)).apply().updates.single().run {
            audioCodec shouldBe "flac"
            bitRate shouldBe 1411
            sampleRate shouldBe 96000
            channelCount shouldBe 2
        }
    }

    @Test
    fun `a re-import that reports no format keeps the stored one`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "mp3", bitRate = 320)

        SongDiff(listOf(existing), listOf(createSong(id = 0, lastModified = firstImport))).apply().updates shouldBe emptyList()
    }

    @Test
    fun `a re-encoded song whose server omits the bit rate and sample rate clears the stored ones`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(sampleRate = 96000, channelCount = 2)
        val reencoded = createSong(id = 0, lastModified = firstImport, audioCodec = "mp3")

        SongDiff(listOf(existing), listOf(reencoded)).apply().updates.single().run {
            audioCodec shouldBe "mp3"
            bitRate shouldBe null
            sampleRate shouldBe null
            channelCount shouldBe null
        }
    }

    @Test
    fun `a remote song whose size changed clears the stored stream properties the server no longer reports`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(sampleRate = 96000)
        val resized = createSong(id = 0, lastModified = firstImport).copy(size = 1234)

        SongDiff(listOf(existing), listOf(resized)).apply().updates.single().run {
            audioCodec shouldBe null
            bitRate shouldBe null
            sampleRate shouldBe null
        }
    }

    @Test
    fun `a local song's tag edit keeps the stored stream properties`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(mediaProvider = MediaProviderType.Shuttle)
        val edited = createSong(id = 0, lastModified = Instant.fromEpochSeconds(1_800_000_000)).copy(mediaProvider = MediaProviderType.Shuttle, size = 99)

        SongDiff(listOf(existing), listOf(edited)).apply().updates.single().run {
            audioCodec shouldBe "flac"
            bitRate shouldBe 1411
        }
    }

    @Test
    fun `a remote song with no modified time keeps the stored stream properties`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(sampleRate = 96000)
        val unchanged = createSong(id = 0, lastModified = null).copy(dateAdded = existing.dateAdded)

        SongDiff(listOf(existing), listOf(unchanged)).update(existing, unchanged).run {
            audioCodec shouldBe "flac"
            bitRate shouldBe 1411
            sampleRate shouldBe 96000
        }
    }

    @Test
    fun `a remote song with a new modified time clears the stored stream properties`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411)
        val touched = createSong(id = 0, lastModified = Instant.fromEpochSeconds(1_800_000_000))

        SongDiff(listOf(existing), listOf(touched)).update(existing, touched).run {
            audioCodec shouldBe null
            bitRate shouldBe null
        }
    }

    @Test
    fun `placeholder size and mime type are not a re-encode`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411).copy(size = 5000, mimeType = "audio/flac")
        val listed = createSong(id = 0, lastModified = firstImport).copy(size = 0, mimeType = "Audio/*")

        SongDiff(listOf(existing), listOf(listed)).update(existing, listed).run {
            audioCodec shouldBe "flac"
            bitRate shouldBe 1411
        }
    }

    @Test
    fun `a codec differing only in case is not a re-encode`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitRate = 1411)
        val listed = createSong(id = 0, lastModified = firstImport, audioCodec = "FLAC")

        SongDiff(listOf(existing), listOf(listed)).update(existing, listed).bitRate shouldBe 1411
    }

    @Test
    fun `an unchanged lossless song keeps its stored bit depth`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitDepth = 24)
        val listed = createSong(id = 0, lastModified = firstImport, audioCodec = "flac")

        SongDiff(listOf(existing), listOf(listed)).update(existing, listed).bitDepth shouldBe 24
    }

    @Test
    fun `a re-encoded song clears the stored bit depth`() = runTest {
        val existing = createSong(id = 7, lastModified = firstImport, audioCodec = "flac", bitDepth = 24)
        val listed = createSong(id = 0, lastModified = firstImport, audioCodec = "alac")

        SongDiff(listOf(existing), listOf(listed)).update(existing, listed).bitDepth shouldBe null
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
    fun `a remote song's play stats are the larger count and the later time of the server's and the stored ones`() = runTest {
        val later = firstImport + 1.days
        val stored = createSong(id = 7, lastModified = firstImport).copy(playCount = 5, lastPlayed = later)

        // The server counts fewer plays (some were made offline) but played it earlier: nothing to write
        val behind = createSong(id = 0, lastModified = firstImport).copy(playCount = 3, lastPlayed = firstImport)
        SongDiff(listOf(stored), listOf(behind)).apply().updates shouldBe emptyList()

        // The server counts more plays, and a later one: both are taken, not added to the stored ones
        val ahead = createSong(id = 0, lastModified = firstImport).copy(playCount = 8, lastPlayed = later + 1.days)
        val update = SongDiff(listOf(stored), listOf(ahead)).apply().updates.single()
        update.playCount shouldBe 8
        update.lastPlayed shouldBe later + 1.days
    }

    @Test
    fun `a server that reports no plays keeps the stored play stats`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport).copy(playCount = 5, lastPlayed = firstImport)

        SongDiff(listOf(stored), listOf(createSong(id = 0, lastModified = firstImport).copy(lastModified = firstImport + 1.days))).apply()
            .updates.single().let { update ->
                update.playCount shouldBe 5
                update.lastPlayed shouldBe firstImport
            }
    }

    @Test
    fun `a local song keeps its own play stats on a rescan`() = runTest {
        val stored = createSong(id = 7, lastModified = firstImport).copy(mediaProvider = MediaProviderType.Shuttle, playCount = 2)
        val rescanned = stored.copy(id = 0, playCount = 9, lastModified = firstImport + 1.days)

        SongDiff(listOf(stored), listOf(rescanned)).apply().updates.single().playCount shouldBe 2
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
        audioCodec: String? = null,
        bitRate: Int? = null
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
        bitRate = bitRate,
        bitDepth = bitDepth,
        sampleRate = null,
        channelCount = null,
        artworkVersion = artworkVersion,
        dateAdded = dateAdded,
        audioCodec = audioCodec
    )
}
