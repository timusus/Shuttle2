package com.simplecityapps.localmediaprovider.local.provider.taglib

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class MovedSongRemapsTest {
    private val oldPath = "/storage/emulated/0/Music/a.mp3"
    private val newPath = "/storage/emulated/0/Albums/a.mp3"

    @Test
    fun `the song at the old path is moved to the new one, keeping its row`() {
        movedSongRemaps(listOf(song(1, oldPath), song(2, "/storage/emulated/0/Music/b.mp3")), listOf(moved())) shouldBe
            listOf(SongPathRemap(songId = 1, path = newPath))
    }

    @Test
    fun `a song already stored at the new path is left alone`() {
        movedSongRemaps(listOf(song(1, oldPath), song(2, newPath)), listOf(moved())).shouldBeEmpty()
    }

    @Test
    fun `a move with no song at the old path remaps nothing`() {
        movedSongRemaps(listOf(song(2, "/storage/emulated/0/Music/b.mp3")), listOf(moved())).shouldBeEmpty()
    }

    private fun moved() = MovedFile(
        oldPath,
        MediaStoreAudioFile(id = 10, path = newPath, displayName = "a.mp3", size = 5_000, lastModified = 1_700_000_000_000, mimeType = "audio/mpeg", duration = 180_000)
    )

    private fun song(
        id: Long,
        path: String
    ) = Song(
        id = id,
        name = "Song $id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 5_000,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(1_700_000_000_000),
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
