package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Which of an artist's songs stand in for them when a source finds artwork through a song. */
class FirstSongTest {
    private val radiohead = AlbumArtist(
        name = "Radiohead",
        artists = listOf("Radiohead"),
        albumCount = 1,
        songCount = 3,
        playCount = 0,
        groupKey = AlbumArtistGroupKey("radiohead"),
        mediaProviders = listOf(MediaProviderType.Shuttle)
    )

    @Test
    fun `an artist's own albums come first including one featuring another or of several album artists`() = runBlocking<Unit> {
        val credited = song(1, albumArtist = "Björk", artists = listOf("Björk", "Radiohead"))
        val featuring = song(2, albumArtist = "Radiohead feat. Björk")
        val several = song(3, albumArtist = null, albumArtists = listOf("Thom Yorke", "Radiohead"))

        FakeSongRepository(listOf(credited, featuring, several)).songsOf(radiohead).map { it.id } shouldBe listOf(2L, 3L, 1L)
    }

    private fun song(
        id: Long,
        albumArtist: String?,
        artists: List<String> = listOf("Radiohead"),
        albumArtists: List<String>? = null
    ) = Song(
        id = id,
        name = "Song $id",
        albumArtist = albumArtist,
        albumArtists = albumArtists,
        artists = artists,
        album = "Album $id",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/$id.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
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
