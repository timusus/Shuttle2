package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class ArtistHeroArtworkTest {
    private val radiohead = artist("Radiohead")
    private val mbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    @Test
    fun `the online lookup is trusted when their own songs carry one MusicBrainz album artist id`() {
        val songs = listOf(
            song("Airbag", albumArtist = "Radiohead", mbAlbumArtistIds = listOf(mbid)),
            song("Paranoid Android", albumArtist = "Radiohead", mbAlbumArtistIds = listOf(mbid.uppercase())),
            // A song without one doesn't count against it
            song("Subterranean Homesick Alien", albumArtist = "Radiohead"),
        )

        ArtistHeroArtwork.of(radiohead, albums = emptyList(), songs = songs).onlineLookup shouldBe true
    }

    @Test
    fun `the online lookup isn't trusted without a MusicBrainz id - however exactly the name matches`() {
        val songs = listOf(song("Airbag", albumArtist = "Radiohead"))

        ArtistHeroArtwork.of(radiohead, albums = emptyList(), songs = songs).onlineLookup shouldBe false
    }

    @Test
    fun `the online lookup isn't trusted when their songs disagree on the id`() {
        val songs = listOf(
            song("Airbag", albumArtist = "Radiohead", mbAlbumArtistIds = listOf(mbid)),
            song("Creep", albumArtist = "Radiohead", mbAlbumArtistIds = listOf("0383dadf-2a4e-4d10-a46a-e9e041da8eb3")),
        )

        ArtistHeroArtwork.of(radiohead, albums = emptyList(), songs = songs).onlineLookup shouldBe false
    }

    @Test
    fun `a song crediting several album artists says nothing about which id is theirs`() {
        val songs = listOf(song("Airbag", albumArtist = "Radiohead", mbAlbumArtistIds = listOf(mbid, "0383dadf-2a4e-4d10-a46a-e9e041da8eb3")))

        ArtistHeroArtwork.of(radiohead, albums = emptyList(), songs = songs).onlineLookup shouldBe false
    }

    @Test
    fun `a credited-only artist's id comes from the songs that credit only them`() {
        val featured = artist("Thom Yorke")
        val thomId = "8ed2e0b3-aa4c-4e13-bec3-dc7393ed4d6b"
        val solo = song("Rabbit in Your Headlights", albumArtist = "UNKLE", artists = listOf("Thom Yorke"), mbArtistIds = listOf(thomId))
        val duet = song("Duet", albumArtist = "Someone", artists = listOf("Thom Yorke; Björk"), mbArtistIds = listOf(thomId, "87c5dedd-371d-4a53-9f7f-80522fb7f3cb"))

        ArtistHeroArtwork.musicBrainzArtistId(featured, listOf(solo, duet)) shouldBe thomId
    }

    @Test
    fun `Various Artists' MusicBrainz id is no one artist's image`() {
        val various = artist("Various Artists")
        val songs = listOf(song("Track", albumArtist = "Various Artists", mbAlbumArtistIds = listOf("89ad4ac3-39f7-470e-963a-56509c546377")))

        ArtistHeroArtwork.of(various, albums = emptyList(), songs = songs).onlineLookup shouldBe false
    }

    @Test
    fun `the fallback is their most played album`() {
        val albums = listOf(album("In Rainbows", year = 2007, playCount = 3), album("OK Computer", year = 1997, playCount = 12), album("Kid A", year = 2000))

        ArtistHeroArtwork.of(radiohead, albums = albums, songs = emptyList()).fallbackAlbum?.name shouldBe "OK Computer"
    }

    @Test
    fun `with nothing played the fallback is their newest album`() {
        val albums = listOf(album("OK Computer", year = 1997), album("A Moon Shaped Pool", year = 2016), album("Untitled", year = null))

        ArtistHeroArtwork.topAlbum(albums)?.name shouldBe "A Moon Shaped Pool"
    }

    @Test
    fun `the date added breaks a tie between albums of the same year`() {
        val albums = listOf(
            album("Earlier", year = 2001, dateAdded = Instant.fromEpochSeconds(1_000)),
            album("Later", year = 2001, dateAdded = Instant.fromEpochSeconds(2_000)),
        )

        ArtistHeroArtwork.topAlbum(albums)?.name shouldBe "Later"
    }

    @Test
    fun `a credited-only artist falls back to the top album they appear on`() {
        val appearsOn = listOf(album("Psyence Fiction", year = 1998, playCount = 2))

        ArtistHeroArtwork.of(artist("Thom Yorke"), albums = emptyList(), songs = emptyList(), appearsOn = appearsOn).fallbackAlbum?.name shouldBe "Psyence Fiction"
    }

    @Test
    fun `no albums at all leaves no fallback`() {
        ArtistHeroArtwork.of(radiohead, albums = emptyList(), songs = emptyList()).fallbackAlbum shouldBe null
    }

    private fun artist(name: String) = AlbumArtist(
        name = name,
        artists = listOf(name),
        albumCount = 1,
        songCount = 1,
        playCount = 0,
        groupKey = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name)),
        mediaProviders = emptyList(),
    )

    private fun album(name: String, year: Int?, playCount: Int = 0, dateAdded: Instant? = null) = Album(
        name = name,
        albumArtist = "Radiohead",
        artists = listOf("Radiohead"),
        songCount = 1,
        duration = 0,
        year = year,
        playCount = playCount,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = AlbumGroupKey(name.lowercase(), radiohead.groupKey),
        mediaProviders = emptyList(),
        dateAdded = dateAdded,
    )

    private var nextId = 1L

    private fun song(
        name: String,
        albumArtist: String,
        artists: List<String> = listOf(albumArtist),
        mbArtistIds: List<String>? = null,
        mbAlbumArtistIds: List<String>? = null,
    ) = Song(
        id = nextId++,
        name = name,
        albumArtist = albumArtist,
        artists = artists,
        album = "$albumArtist album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
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
        mbArtistIds = mbArtistIds,
        mbAlbumArtistIds = mbAlbumArtistIds,
    )
}
