package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class SongListSortTest {
    private val songs = listOf(
        song("Rain", album = "Old Coat", track = 2, added = 100, year = 1999),
        song("Glass", album = "New Leaf", track = 1, added = 300, year = 2004),
        song("Ash", album = "Old Coat", track = 1, added = 200, year = 1999),
        song("Moss", album = "New Leaf", track = 2, added = 50, year = 2004),
    )

    private fun List<SongSection>.layout() = map { (it.album?.name ?: "-") to it.songs.map { song -> song.name } }

    @Test
    fun `album newest added puts the album with the latest addition first with songs in track order`() {
        songSections(songs, SongListSort.AlbumNewestAdded).layout() shouldBe listOf(
            "New Leaf" to listOf("Glass", "Moss"),
            "Old Coat" to listOf("Ash", "Rain"),
        )
    }

    @Test
    fun `album title orders sections A to Z`() {
        songSections(songs, SongListSort.AlbumTitle).layout() shouldBe listOf(
            "New Leaf" to listOf("Glass", "Moss"),
            "Old Coat" to listOf("Ash", "Rain"),
        )
    }

    @Test
    fun `album newest orders sections by year`() {
        val older = songs + song("Dust", album = "Attic", track = 1, added = 10, year = 1980)

        songSections(older, SongListSort.AlbumNewest).layout().map { it.first } shouldBe listOf("New Leaf", "Old Coat", "Attic")
    }

    @Test
    fun `playlist order keeps the songs as they arrive in one flat section`() {
        songSections(songs, SongListSort.PlaylistOrder).layout() shouldBe listOf("-" to listOf("Rain", "Glass", "Ash", "Moss"))
    }

    @Test
    fun `newest added lists the latest addition first`() {
        songSections(songs, SongListSort.NewestAdded).layout() shouldBe listOf("-" to listOf("Glass", "Ash", "Rain", "Moss"))
    }

    @Test
    fun `song title lists flat A to Z`() {
        songSections(songs, SongListSort.SongTitle).layout() shouldBe listOf("-" to listOf("Ash", "Glass", "Moss", "Rain"))
    }

    @Test
    fun `songs off every given album trail in a section with no album`() {
        val oneAlbum = albumsOf(songs).filter { it.name == "Old Coat" }

        songSections(songs, SongListSort.AlbumTitle, oneAlbum).layout() shouldBe listOf(
            "Old Coat" to listOf("Ash", "Rain"),
            "-" to listOf("Glass", "Moss"),
        )
    }

    @Test
    fun `no songs make no sections`() {
        songSections(emptyList(), SongListSort.AlbumTitle) shouldBe emptyList()
    }

    @Test
    fun `albums built from songs carry their year and count and newest addition`() {
        val leaf = albumsOf(songs).single { it.name == "New Leaf" }

        leaf.year shouldBe 2004
        leaf.songCount shouldBe 2
        leaf.dateAdded shouldBe Instant.fromEpochSeconds(300)
    }

    @Test
    fun `album ranked keeps albums in the order they first arrive with songs in track order`() {
        val arriving = listOf(songs[2], songs[1], songs[0], songs[3])

        songSections(arriving, SongListSort.AlbumRanked).layout() shouldBe listOf(
            "Old Coat" to listOf("Ash", "Rain"),
            "New Leaf" to listOf("Glass", "Moss"),
        )
    }

    @Test
    fun `only the album sorts group`() {
        SongListSort.entries.filter { it.groupsByAlbum } shouldBe
            listOf(SongListSort.AlbumNewestAdded, SongListSort.AlbumNewest, SongListSort.AlbumOldest, SongListSort.AlbumTitle, SongListSort.AlbumRanked)
    }

    private fun song(name: String, album: String, track: Int, added: Long, year: Int) = Song(
        id = name.hashCode().toLong(),
        name = name,
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = album,
        track = track,
        disc = 1,
        duration = 180_000,
        date = LocalDate(year, 1, 1),
        genres = emptyList(),
        path = "/music/$name.mp3",
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
        channelCount = null,
        dateAdded = Instant.fromEpochSeconds(added),
    )
}
