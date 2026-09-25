package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeSongRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test

class WriteSongTagsTest {
    private val tagFileAccess = FakeTagFileAccess()
    private val songRepository = FakeSongRepository()
    private val playbackManager = FakePlaybackManager()
    private val writeSongTags = WriteSongTags(tagFileAccess, songRepository, playbackManager)

    @Test
    fun `writes only the changed fields`() = runTest {
        val song = createSong(id = 1)

        writeSongTags(listOf(EditableSong(song, createAudioFile())), mapOf(TagField.Album to "New Album", TagField.Year to "1999"))

        tagFileAccess.writes.single().second shouldBe mapOf("ALBUM" to listOf("New Album"), "DATE" to listOf("1999"))
    }

    @Test
    fun `updates the library and the queue with the new tags`() = runTest {
        val song = createSong(id = 1, name = "Old", album = "Old Album")

        val result = writeSongTags(
            listOf(EditableSong(song, createAudioFile())),
            mapOf(TagField.Title to "New", TagField.Artists to "A, B ,", TagField.Year to "1999", TagField.Genres to "Rock, Pop", TagField.Track to "4"),
        )

        val updated = song.copy(name = "New", artists = listOf("A", "B"), date = LocalDate(1999, 1, 1), genres = listOf("Rock", "Pop"), track = 4)
        result shouldBe TagWriteResult(updated = listOf(updated), failed = emptyList())
        songRepository.updatedSongs shouldBe listOf(updated)
        playbackManager.queueSongUpdates shouldBe listOf(listOf(updated))
    }

    @Test
    fun `a failed write leaves the library alone and is reported`() = runTest {
        val good = createSong(id = 1)
        val bad = createSong(id = 2)
        tagFileAccess.failingSongIds = setOf(2)

        val result = writeSongTags(listOf(EditableSong(good, createAudioFile()), EditableSong(bad, createAudioFile())), mapOf(TagField.Album to "New"))

        result.updated.map { it.id } shouldBe listOf(1L)
        result.failed shouldBe listOf(bad)
        songRepository.updatedSongs.map { it.id } shouldBe listOf(1L)
    }

    @Test
    fun `a track total change keeps each song's own track number`() = runTest {
        val songs = listOf(EditableSong(createSong(id = 1), createAudioFile(track = 3)), EditableSong(createSong(id = 2), createAudioFile(track = 11)))

        writeSongTags(songs, mapOf(TagField.TrackTotal to "12"))

        tagFileAccess.writes.map { it.second } shouldBe listOf(mapOf("TRACKNUMBER" to listOf("03/12")), mapOf("TRACKNUMBER" to listOf("11/12")))
    }

    @Test
    fun `a disc number is written with the file's own total`() = runTest {
        writeSongTags(listOf(EditableSong(createSong(id = 1), createAudioFile(disc = 1, discTotal = 2))), mapOf(TagField.Disc to "2"))

        tagFileAccess.writes.single().second shouldBe mapOf("DISCNUMBER" to listOf("02/02"))
    }

    @Test
    fun `an emptied track number clears the tag`() = runTest {
        writeSongTags(listOf(EditableSong(createSong(id = 1), createAudioFile(track = 3))), mapOf(TagField.Track to ""))

        tagFileAccess.writes.single().second shouldBe mapOf("TRACKNUMBER" to emptyList())
    }

    @Test
    fun `an emptied field is written as an empty list, which removes it from the tag`() {
        metadata(createAudioFile(), mapOf(TagField.Title to "", TagField.Album to "  ", TagField.Genres to "Rock")) shouldBe
            mapOf("TITLE" to emptyList(), "ALBUM" to emptyList(), "GENRE" to listOf("Rock"))
    }

    @Test
    fun `non-ASCII values are passed through unchanged`() {
        val title = "Café — Ünïcødé 日本語 🎵"

        metadata(createAudioFile(), mapOf(TagField.Title to title, TagField.Artists to "Björk")) shouldBe
            mapOf("TITLE" to listOf(title), "ARTIST" to listOf("Björk"))
    }

    @Test
    fun `no changes writes nothing`() = runTest {
        val result = writeSongTags(listOf(EditableSong(createSong(id = 1), createAudioFile())), emptyMap())

        result shouldBe TagWriteResult(emptyList(), emptyList())
        tagFileAccess.writes shouldBe emptyList()
        songRepository.updatedSongs shouldBe emptyList()
    }
}
