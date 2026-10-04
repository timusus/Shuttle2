package com.simplecityapps.shuttle.model

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlbumIdentityRuleTest {
    private var nextId = 1L

    private fun tags(
        album: String?,
        artists: List<String> = listOf("Artist"),
        albumArtist: String? = null,
        albumArtists: List<String>? = null,
        compilation: Boolean? = null,
        mbAlbumId: String? = null,
        serverAlbumId: String? = null,
        mediaProvider: MediaProviderType = MediaProviderType.Shuttle,
        path: String = "/music/${album ?: "none"}/$nextId.mp3"
    ) = AlbumIdentityTags(nextId++, album, albumArtist, albumArtists, artists, compilation, mbAlbumId, serverAlbumId, mediaProvider, path)

    private fun resolve(vararg songs: AlbumIdentityTags): List<AlbumIdentity> {
        val identities = AlbumIdentityRule.resolve(songs.toList())
        return songs.map { identities.getValue(it.songId) }
    }

    @Test
    fun `an album artist tag names the album - as before the rule`() {
        val (song) = resolve(tags("The Bends", artists = listOf("Radiohead"), albumArtist = "Radiohead"))

        song.groupKey shouldBe AlbumGroupKey("bends", AlbumArtistGroupKey("radiohead"))
        song.albumArtistName shouldBe "Radiohead"
    }

    @Test
    fun `the album artists tag names the album when there's no album artist tag`() {
        val (song) = resolve(tags("Watch the Throne", albumArtists = listOf("Jay-Z", "Kanye West")))

        song.groupKey shouldBe AlbumGroupKey("watch the throne", AlbumArtistGroupKey("jay-z kanye west"))
        song.albumArtistName shouldBe "Jay-Z, Kanye West"
    }

    @Test
    fun `songs of one MusicBrainz release are one album - however their tags differ`() {
        val songs = resolve(
            tags("OK Computer", albumArtist = "Radiohead", mbAlbumId = "0B6B4BA0"),
            tags("OK Computer", albumArtist = "Radiohead", mbAlbumId = "0b6b4ba0"),
            tags("OK Computer (Remastered)", albumArtist = "Radiohead", mbAlbumId = "0b6b4ba0")
        )

        songs.map { it.groupKey }.distinct() shouldHaveSize 1
        songs.first().groupKey shouldBe AlbumGroupKey("ok computer", AlbumArtistGroupKey("radiohead"), "mb:0b6b4ba0")
    }

    @Test
    fun `two releases of one name are two albums by their MusicBrainz ids`() {
        val songs = resolve(
            tags("Greatest Hits", albumArtist = "Queen", mbAlbumId = "a"),
            tags("Greatest Hits", albumArtist = "Queen", mbAlbumId = "b")
        )

        songs.map { it.groupKey.identity } shouldBe listOf("mb:a", "mb:b")
    }

    @Test
    fun `a server's album id keys the album - scoped to its source`() {
        val songs = resolve(
            tags("Blue", albumArtist = "Joni Mitchell", serverAlbumId = "42", mediaProvider = MediaProviderType.Jellyfin),
            tags("Blue", albumArtist = "Joni Mitchell", serverAlbumId = "42", mediaProvider = MediaProviderType.Jellyfin)
        )

        songs.map { it.groupKey }.distinct() shouldBe listOf(AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell"), "jellyfin:42"))
    }

    @Test
    fun `a compilation without an album artist is Various Artists'`() {
        val songs = resolve(
            tags("Now 100", artists = listOf("Adele"), compilation = true),
            tags("Now 100", artists = listOf("Dua Lipa"), compilation = true)
        )

        songs.map { it.groupKey }.distinct() shouldBe listOf(AlbumGroupKey("now 100", AlbumArtistGroupKey("various artists")))
        songs.first().albumArtistName shouldBe AlbumIdentityRule.VARIOUS_ARTISTS
    }

    @Test
    fun `an untagged album whose tracks agree on the artist is theirs - features and disc folders aside`() {
        val songs = resolve(
            tags("Blonde", artists = listOf("Frank Ocean"), path = "/music/Blonde/CD1/1.mp3"),
            tags("Blonde", artists = listOf("Frank Ocean feat. André 3000"), path = "/music/Blonde/CD1/2.mp3"),
            tags("Blonde", artists = listOf("Frank Ocean"), path = "/music/Blonde/CD2/1.mp3")
        )

        songs.map { it.groupKey }.distinct() shouldBe listOf(AlbumGroupKey("blonde", AlbumArtistGroupKey("frank ocean")))
        songs.first().albumArtistName shouldBe "Frank Ocean"
    }

    @Test
    fun `an untagged album of several artists is Various Artists' - told apart by its folder`() {
        val songs = resolve(
            tags("Drive OST", artists = listOf("Kavinsky"), path = "/music/Drive/1.mp3"),
            tags("Drive OST", artists = listOf("College"), path = "/music/Drive/2.mp3"),
            tags("Drive OST", artists = listOf("Desire"), path = "/other/Drive/1.mp3"),
            tags("Drive OST", artists = listOf("Chromatics"), path = "/other/Drive/2.mp3")
        )

        songs.map { it.groupKey }.distinct() shouldBe listOf(
            AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/music/Drive"),
            AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/other/Drive")
        )
        songs.first().albumArtistName shouldBe AlbumIdentityRule.VARIOUS_ARTISTS
    }

    @Test
    fun `a document URI's folder is found through its encoded separators`() {
        val songs = resolve(
            tags("Mix", artists = listOf("A"), path = "content://tree/primary%3AMusic%2FMix%2F1.mp3"),
            tags("Mix", artists = listOf("B"), path = "content://tree/primary%3AMusic%2FMix%2F2.mp3")
        )

        songs.map { it.groupKey.identity }.distinct() shouldBe listOf("dir:content://tree/primary%3AMusic%2FMix")
    }

    @Test
    fun `a mixed album - where only some tracks have an id - stays one album on the name rule`() {
        val songs = resolve(
            tags("In Rainbows", albumArtist = "Radiohead", mbAlbumId = "r1", serverAlbumId = "9"),
            tags("In Rainbows", albumArtist = "Radiohead", mbAlbumId = "r1"),
            tags("In Rainbows", albumArtist = "Radiohead")
        )

        songs.map { it.groupKey }.distinct() shouldBe listOf(AlbumGroupKey("in rainbows", AlbumArtistGroupKey("radiohead")))
    }

    @Test
    fun `songs without an album name aren't gathered into one album`() {
        val songs = resolve(
            tags(null, artists = listOf("Björk")),
            tags(null, artists = listOf("Low"))
        )

        songs.map { it.groupKey } shouldBe listOf(
            AlbumGroupKey(null, AlbumArtistGroupKey("björk")),
            AlbumGroupKey(null, AlbumArtistGroupKey("low"))
        )
    }

    @Test
    fun `a song holding no identity resolves its own`() {
        val song = Song(
            id = 1, name = "Song", albumArtist = null, artists = listOf("Low"), album = "Things We Lost in the Fire",
            track = 1, disc = 1, duration = 0, date = null, genres = emptyList(), path = "/music/1.mp3", size = 0,
            mimeType = "audio/mpeg", lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0,
            playbackPosition = 0, blacklisted = false, mediaProvider = MediaProviderType.Shuttle, lyrics = null,
            grouping = null, bitRate = null, bitDepth = null, sampleRate = null, channelCount = null
        )

        song.albumGroupKey shouldBe AlbumGroupKey("things we lost in the fire", AlbumArtistGroupKey("low"))
        song.albumArtistGroupKey shouldBe AlbumArtistGroupKey("low")
    }
}
