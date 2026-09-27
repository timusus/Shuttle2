package com.simplecityapps.localmediaprovider.local.provider.mediastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.provider.FileTags
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreFileTagsTest {
    @Test
    fun `song gets ReplayGain values from the tag reader`() {
        val reader = MediaStoreTagReader { _, _ -> fileTags(replayGainTrack = -3.5, replayGainAlbum = -4.2) }

        val result = runBlocking { createMediaStoreSong().withFileTags(reader) }

        result.replayGainTrack shouldBe -3.5
        result.replayGainAlbum shouldBe -4.2
    }

    @Test
    fun `a failing read leaves ReplayGain values null`() {
        val reader = MediaStoreTagReader { _, _ -> null }

        val result = runBlocking { createMediaStoreSong().withFileTags(reader) }

        result.replayGainTrack shouldBe null
        result.replayGainAlbum shouldBe null
    }

    @Test
    fun `a reader that throws leaves ReplayGain values null`() {
        val reader = MediaStoreTagReader { _, _ -> throw IllegalStateException("corrupt file") }

        val result = runBlocking { createMediaStoreSong().withFileTags(reader) }

        result.replayGainTrack shouldBe null
        result.replayGainAlbum shouldBe null
    }

    @Test
    fun `one unreadable file doesn't stop the other songs importing`() {
        val reader = MediaStoreTagReader { uri, _ ->
            if (uri.lastPathSegment == "2") throw IllegalArgumentException("corrupt file")
            fileTags(replayGainTrack = -3.5, replayGainAlbum = -4.2)
        }
        val songs = (1..3).map { id -> createMediaStoreSong(externalId = "$id", path = "/music/$id.mp3") }

        val result = runBlocking { songs.withFileTags(emptyList(), reader, readUnchanged = false).toList() }

        result.sortedBy { it.path }.map { it.replayGainTrack } shouldBe listOf(-3.5, null, -3.5)
    }

    @Test
    fun `an unchanged file keeps its stored values without being read`() {
        var readCount = 0
        val reader = MediaStoreTagReader { _, _ ->
            readCount++
            fileTags(replayGainTrack = 1.0, replayGainAlbum = 1.0)
        }
        val existing = createMediaStoreSong(replayGainTrack = -3.5, replayGainAlbum = -4.2)

        val result = runBlocking { listOf(createMediaStoreSong()).withFileTags(listOf(existing), reader, readUnchanged = false).toList() }

        readCount shouldBe 0
        result.single().replayGainTrack shouldBe -3.5
        result.single().replayGainAlbum shouldBe -4.2
    }

    @Test
    fun `a new, modified or resized file is read`() {
        val reader = MediaStoreTagReader { _, _ -> fileTags(replayGainTrack = -1.0, replayGainAlbum = -2.0) }
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

        val result = runBlocking { songs.withFileTags(existing, reader, readUnchanged = false).toList() }

        result.map { it.replayGainTrack } shouldBe listOf(-1.0, -1.0, -1.0)
    }

    @Test
    fun `the backfill reads unchanged files too`() {
        val reader = MediaStoreTagReader { _, _ -> fileTags(replayGainTrack = -3.5, replayGainAlbum = -4.2) }
        val existing = createMediaStoreSong()

        val result = runBlocking { listOf(createMediaStoreSong()).withFileTags(listOf(existing), reader, readUnchanged = true).toList() }

        result.single().replayGainTrack shouldBe -3.5
        result.single().replayGainAlbum shouldBe -4.2
    }

    @Test
    fun `a song without a MediaStore id is left untouched`() {
        var readCalled = false
        val reader = MediaStoreTagReader { _, _ ->
            readCalled = true
            fileTags(replayGainTrack = 1.0, replayGainAlbum = 1.0)
        }
        val song = createMediaStoreSong(externalId = null)

        val result = runBlocking { song.withFileTags(reader) }

        result shouldBe song
        readCalled shouldBe false
    }

    @Test
    fun `a Matroska file's tags replace the file and folder names MediaStore falls back to`() {
        // MediaStore doesn't read Matroska tags: it titles the song after the file and names the album after the folder
        val song = createMediaStoreSong(name = "gapless2", artists = emptyList(), albumArtist = null, album = "gapless", track = null, date = null)
        val reader =
            MediaStoreTagReader { _, _ ->
                fileTags(title = "Gapless Two", artists = listOf("Gapless Artist"), albumArtist = "Gapless Artist", album = "Gapless Album", track = 2, year = "2023")
            }

        val result = runBlocking { song.withFileTags(reader) }

        result.name shouldBe "Gapless Two"
        result.artists shouldBe listOf("Gapless Artist")
        result.albumArtist shouldBe "Gapless Artist"
        result.album shouldBe "Gapless Album"
        result.track shouldBe 2
        result.date shouldBe LocalDate(2023, 1, 1)
    }

    @Test
    fun `a field the file doesn't tag keeps MediaStore's value`() {
        val song = createMediaStoreSong(name = "gapless2")
        val reader = MediaStoreTagReader { _, _ -> fileTags(album = "Gapless Album") }

        val result = runBlocking { song.withFileTags(reader) }

        result.name shouldBe "gapless2"
        result.album shouldBe "Gapless Album"
        result.artists shouldBe listOf("Test Artist")
        result.track shouldBe 1
    }

    @Test
    fun `non-ASCII tags come from the file, not MediaStore's mis-decoded copy`() {
        // #150: MediaStore decoded this UTF-16 ID3v2.3 title as Windows-1252 bytes
        val song = createMediaStoreSong(name = "Chanson dâ€™un jour dâ€™hiver", artists = listOf("Ã‘engo Flow"), album = "æ±äº¬")
        val reader =
            MediaStoreTagReader { _, _ ->
                fileTags(title = "Chanson d’un jour d’hiver", artists = listOf("Ñengo Flow"), album = "東京 🎵")
            }

        val result = runBlocking { song.withFileTags(reader) }

        result.name shouldBe "Chanson d’un jour d’hiver"
        result.artists shouldBe listOf("Ñengo Flow")
        result.album shouldBe "東京 🎵"
    }

    @Test
    fun `a file TagLib can't parse keeps MediaStore's values`() {
        val song = createMediaStoreSong()
        val reader = MediaStoreTagReader { _, _ -> null }

        val result = runBlocking { song.withFileTags(reader) }

        result shouldBe song
    }

    @Test
    fun `an unchanged file keeps its stored tags without being read`() {
        val reader = MediaStoreTagReader { _, _ -> fileTags(title = "Read again", album = "Read again") }
        val existing = createMediaStoreSong(name = "Gapless Two", album = "Gapless Album", artists = listOf("Gapless Artist"), track = 2)

        val result = runBlocking { listOf(createMediaStoreSong(name = "gapless2", album = "gapless")).withFileTags(listOf(existing), reader, readUnchanged = false).toList() }

        result.single().name shouldBe "Gapless Two"
        result.single().album shouldBe "Gapless Album"
        result.single().artists shouldBe listOf("Gapless Artist")
        result.single().track shouldBe 2
    }

    private fun fileTags(
        title: String? = null,
        artists: List<String> = emptyList(),
        albumArtist: String? = null,
        album: String? = null,
        track: Int? = null,
        year: String? = null,
        replayGainTrack: Double? = null,
        replayGainAlbum: Double? = null
    ) = FileTags(
        title = title,
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        track = track,
        trackTotal = null,
        disc = null,
        discTotal = null,
        year = year,
        genres = emptyList(),
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = null,
        grouping = null
    )

    private fun createMediaStoreSong(
        externalId: String? = "42",
        path: String = "/storage/emulated/0/Music/test.mp3",
        size: Long = 1024L,
        lastModified: Instant = Instant.fromEpochMilliseconds(0),
        replayGainTrack: Double? = null,
        replayGainAlbum: Double? = null,
        name: String = "Test Song",
        artists: List<String> = listOf("Test Artist"),
        albumArtist: String? = "Test Album Artist",
        album: String = "Test Album",
        track: Int? = 1,
        date: LocalDate? = null
    ) = Song(
        id = 0,
        name = name,
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        track = track,
        disc = 1,
        duration = 1000,
        date = date,
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
