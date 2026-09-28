package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ArtistSongSortOrderTest {
    private val albums = listOf(
        album("cassette summer", 1969),
        album("Undated", null),
        album("Loose Change", 1970),
        album("Lantern Hours", 1963),
    )

    private fun sortedAlbums(order: ArtistSongSortOrder) = albums.sortedWith(order.albumComparator!!).map { it.name }

    @Test
    fun `album newest puts the latest year first and undated albums last`() {
        sortedAlbums(ArtistSongSortOrder.AlbumNewest) shouldBe listOf("Loose Change", "cassette summer", "Lantern Hours", "Undated")
    }

    @Test
    fun `album oldest puts the earliest year first and undated albums last`() {
        sortedAlbums(ArtistSongSortOrder.AlbumOldest) shouldBe listOf("Lantern Hours", "cassette summer", "Loose Change", "Undated")
    }

    @Test
    fun `album title orders by letter regardless of case`() {
        sortedAlbums(ArtistSongSortOrder.AlbumTitle) shouldBe listOf("cassette summer", "Lantern Hours", "Loose Change", "Undated")
    }

    @Test
    fun `album orders group by album and flat orders don't`() {
        ArtistSongSortOrder.entries.filter { it.groupsByAlbum } shouldBe
            listOf(ArtistSongSortOrder.AlbumNewest, ArtistSongSortOrder.AlbumOldest, ArtistSongSortOrder.AlbumTitle)
        ArtistSongSortOrder.Default shouldBe ArtistSongSortOrder.AlbumNewest
    }

    @Test
    fun `album orders list songs in disc then track order`() {
        val songs = listOf(song("D2T1", disc = 2, track = 1), song("D1T2", disc = 1, track = 2), song("D1T1", disc = 1, track = 1))

        songs.sortedWith(ArtistSongSortOrder.AlbumTitle.songComparator).map { it.name } shouldBe listOf("D1T1", "D1T2", "D2T1")
    }

    @Test
    fun `song title orders by letter regardless of case`() {
        val songs = listOf(song("banana"), song("Cherry"), song("apple"))

        songs.sortedWith(ArtistSongSortOrder.SongTitle.songComparator).map { it.name } shouldBe listOf("apple", "banana", "Cherry")
    }

    @Test
    fun `most played puts the most plays first and breaks ties by title`() {
        val songs = listOf(song("b", playCount = 3), song("c", playCount = 9), song("a", playCount = 3))

        songs.sortedWith(ArtistSongSortOrder.MostPlayed.songComparator).map { it.name } shouldBe listOf("c", "a", "b")
    }

    private fun album(name: String, year: Int?) = Album(
        name = name,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        songCount = 1,
        duration = 0,
        year = year,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = AlbumGroupKey(name.lowercase(), AlbumArtistGroupKey("artist")),
        mediaProviders = emptyList(),
    )

    private fun song(name: String, disc: Int? = 1, track: Int? = 1, playCount: Int = 0) = Song(
        id = 0,
        name = name,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = track,
        disc = disc,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = playCount,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
    )
}
