package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ArtistCreditsTest {
    private var nextId = 1L

    private fun tags(
        album: String?,
        artists: List<String>,
        albumArtist: String? = null,
        albumArtists: List<String>? = null,
        compilation: Boolean? = null,
        artistsTag: List<String>? = null,
        mbArtistIds: List<String>? = null,
        mbAlbumArtistIds: List<String>? = null,
        serverArtistIds: List<String>? = null,
        serverAlbumArtistIds: List<String>? = null,
        mediaProvider: MediaProviderType = MediaProviderType.Shuttle
    ) = AlbumIdentityTags(
        songId = nextId++,
        album = album,
        albumArtist = albumArtist,
        albumArtists = albumArtists,
        artists = artists,
        compilation = compilation,
        mbAlbumId = null,
        serverAlbumId = null,
        mediaProvider = mediaProvider,
        path = "/music/${album ?: "none"}/$nextId.mp3",
        artistsTag = artistsTag,
        mbArtistIds = mbArtistIds,
        mbAlbumArtistIds = mbAlbumArtistIds,
        serverArtistIds = serverArtistIds,
        serverAlbumArtistIds = serverAlbumArtistIds
    )

    private fun credits(song: AlbumIdentityTags): List<ArtistCredit> = ArtistCredits.credits(song, AlbumIdentityRule.resolve(listOf(song)).getValue(song.songId))

    private fun key(name: String) = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))

    @Test
    fun `an ARTIST value splits on featuring credits - semicolons and a spaced slash`() {
        ArtistCredits.split("Kanye West feat. Chris Martin") shouldBe listOf("Kanye West", "Chris Martin")
        ArtistCredits.split("Mark Ronson (feat. Bruno Mars)") shouldBe listOf("Mark Ronson", "Bruno Mars")
        ArtistCredits.split("Calvin Harris ft. Rihanna") shouldBe listOf("Calvin Harris", "Rihanna")
        ArtistCredits.split("Santana featuring Rob Thomas") shouldBe listOf("Santana", "Rob Thomas")
        ArtistCredits.split("Brian Eno / David Byrne") shouldBe listOf("Brian Eno", "David Byrne")
        ArtistCredits.split("Daft Punk; Pharrell Williams") shouldBe listOf("Daft Punk", "Pharrell Williams")
    }

    @Test
    fun `an ampersand - a bare slash and a name holding 'ft' never split`() {
        ArtistCredits.split("Simon & Garfunkel") shouldBe listOf("Simon & Garfunkel")
        ArtistCredits.split("AC/DC") shouldBe listOf("AC/DC")
        ArtistCredits.split("Daft Punk") shouldBe listOf("Daft Punk")
        ArtistCredits.split("Sufjan Stevens") shouldBe listOf("Sufjan Stevens")
    }

    @Test
    fun `the ARTISTS multi-value tag names the credits - each value whole`() {
        val song = tags("Watch the Throne", listOf("Jay-Z & Kanye West feat. Frank Ocean"), albumArtists = listOf("Jay-Z", "Kanye West"), artistsTag = listOf("Jay-Z", "Kanye West", "Frank Ocean / Friends"))

        credits(song).map { it.name } shouldBe listOf("Jay-Z", "Kanye West", "Frank Ocean / Friends")
    }

    @Test
    fun `a credit is the album artist's when it carries their MusicBrainz id - however it's spelt`() {
        val song = tags(
            "Lemonade",
            listOf("Beyonce feat. Jack White"),
            albumArtist = "Beyoncé",
            mbArtistIds = listOf("b1", "jw"),
            mbAlbumArtistIds = listOf("B1")
        )

        credits(song) shouldBe listOf(ArtistCredit("Beyonce", key("Beyoncé")), ArtistCredit("Jack White", key("Jack White")))
    }

    @Test
    fun `a credit is the album artist's when it carries their server id - and ids that don't pair with names are ignored`() {
        val paired = tags("Lemonade", listOf("Beyonce"), albumArtist = "Beyoncé", serverArtistIds = listOf("s1"), serverAlbumArtistIds = listOf("s1"), mediaProvider = MediaProviderType.Jellyfin)
        val unpaired = tags("Lemonade", listOf("Beyonce feat. Jack White"), albumArtist = "Beyoncé", serverArtistIds = listOf("s1"), serverAlbumArtistIds = listOf("s1"), mediaProvider = MediaProviderType.Jellyfin)

        credits(paired).map { it.groupKey } shouldBe listOf(key("Beyoncé"))
        credits(unpaired).map { it.groupKey } shouldBe listOf(key("Beyonce"), key("Jack White"))
    }

    @Test
    fun `an artist's songs in a library are their own albums' and those crediting them elsewhere`() {
        val viva = tags("Viva la Vida", listOf("Coldplay"), albumArtist = "Coldplay")
        val graduation = tags("Graduation", listOf("Kanye West feat. Chris Martin"), albumArtist = "Kanye West")
        val nowAdele = tags("Now 100", listOf("Adele"), compilation = true)
        val nowColdplay = tags("Now 100", listOf("Coldplay"), compilation = true)
        val throne = tags("Watch the Throne", listOf("Jay-Z"), albumArtists = listOf("Jay-Z", "Kanye West"), artistsTag = listOf("Jay-Z", "Kanye West", "Frank Ocean"))
        // Untagged, so the agreeing track artist is the album artist: the feature doesn't make it someone else's
        val blonde = tags("Blonde", listOf("Frank Ocean feat. André 3000"))
        val index = AlbumIndex(listOf(viva, graduation, nowAdele, nowColdplay, throne, blonde))

        index.songIds(key("Coldplay")) shouldBe listOf(viva.songId, nowColdplay.songId)
        index.songIds(key("Chris Martin")) shouldBe listOf(graduation.songId)
        index.songIds(key("Kanye West")) shouldBe listOf(graduation.songId, throne.songId)
        index.songIds(key("Frank Ocean")) shouldBe listOf(throne.songId, blonde.songId)
        index.songIds(key("André 3000")) shouldBe listOf(blonde.songId)
        index.songIds(key(AlbumIdentityRule.VARIOUS_ARTISTS)) shouldBe listOf(nowAdele.songId, nowColdplay.songId)
    }

    @Test
    fun `an album artist splits on feat only`() {
        ArtistCredits.splitFeaturing("Calvin Harris feat. Rihanna") shouldBe listOf("Calvin Harris", "Rihanna")
        ArtistCredits.splitFeaturing("Mark Ronson (ft. Bruno Mars)") shouldBe listOf("Mark Ronson", "Bruno Mars")
        ArtistCredits.splitFeaturing("Simon & Garfunkel") shouldBe listOf("Simon & Garfunkel")
        ArtistCredits.splitFeaturing("AC/DC") shouldBe listOf("AC/DC")
    }

    @Test
    fun `an album artist's featured artist is credited on every song - and owns none of it`() {
        val song = tags("Song Album", listOf("Calvin Harris"), albumArtist = "Calvin Harris feat. Rihanna")
        val identity = AlbumIdentityRule.resolve(listOf(song)).getValue(song.songId)

        credits(song).map { it.groupKey } shouldBe listOf(key("Calvin Harris"), key("Rihanna"))
        AlbumIndex(listOf(song)).songIds(key("Rihanna")) shouldBe listOf(song.songId)
        (key("Rihanna") in identity.albumArtistKeys) shouldBe false
    }

    @Test
    fun `each of several album artists has their own server id - when the ids pair with the names`() {
        val paired = tags(
            "Watch the Throne",
            listOf("Jay-Z"),
            albumArtists = listOf("Jay-Z", "Kanye West"),
            serverAlbumArtistIds = listOf("jay", "ye"),
            mediaProvider = MediaProviderType.Jellyfin
        )
        val unpaired = tags(
            "Watch the Throne",
            listOf("Jay-Z"),
            albumArtists = listOf("Jay-Z", "Kanye West"),
            serverAlbumArtistIds = listOf("both"),
            mediaProvider = MediaProviderType.Jellyfin
        )

        serverArtistId(paired, "Jay-Z") shouldBe "jay"
        serverArtistId(paired, "Kanye West") shouldBe "ye"
        serverArtistId(unpaired, "Kanye West") shouldBe null
    }

    @Test
    fun `an artist's songs include every album they're one of the album artists of`() {
        val throne = tags("Watch the Throne", emptyList(), albumArtists = listOf("Jay-Z", "Kanye West"))
        val index = AlbumIndex(listOf(throne))

        index.songIds(key("Jay-Z")) shouldBe listOf(throne.songId)
        index.songIds(key("Kanye West")) shouldBe listOf(throne.songId)
    }

    private fun serverArtistId(song: AlbumIdentityTags, artist: String): String? = ArtistCredits.serverArtistId(song, AlbumIdentityRule.resolve(listOf(song)).getValue(song.songId), key(artist))

    @Test
    fun `an artist's server id is never another artist's on the same song`() {
        // Their own album, where a duet partner is credited first (#653)
        val duet = tags(
            "The Lockdown Sessions",
            emptyList(),
            albumArtist = "Elton John",
            artistsTag = listOf("Dua Lipa", "Elton John"),
            serverArtistIds = listOf("dua", "elton"),
            serverAlbumArtistIds = listOf("elton"),
            mediaProvider = MediaProviderType.Jellyfin
        )

        serverArtistId(duet, "Elton John") shouldBe "elton"
        serverArtistId(duet, "Dua Lipa") shouldBe "dua"
        serverArtistId(duet, "Someone Else") shouldBe null
    }

    @Test
    fun `several album artists' paired ids pin each down - but not their joint name or an unpaired track artist id`() {
        val joint = tags(
            "Blade Runner 2049",
            emptyList(),
            albumArtists = listOf("Hans Zimmer", "Benjamin Wallfisch"),
            artistsTag = listOf("Hans Zimmer", "Benjamin Wallfisch"),
            serverArtistIds = listOf("hans"),
            serverAlbumArtistIds = listOf("hans", "ben"),
            mediaProvider = MediaProviderType.Jellyfin
        )
        val albumArtist = AlbumIdentityRule.resolve(listOf(joint)).getValue(joint.songId).albumArtistName!!

        serverArtistId(joint, albumArtist) shouldBe null
        serverArtistId(joint, "Hans Zimmer") shouldBe "hans"
        serverArtistId(joint, "Benjamin Wallfisch") shouldBe "ben"
    }

    @Test
    fun `Various Artists has no server artist image`() {
        val compilation = tags(
            "Now 100",
            listOf("Adele"),
            albumArtist = AlbumIdentityRule.VARIOUS_ARTISTS,
            serverArtistIds = listOf("adele"),
            serverAlbumArtistIds = listOf("va"),
            mediaProvider = MediaProviderType.Jellyfin
        )

        serverArtistId(compilation, AlbumIdentityRule.VARIOUS_ARTISTS) shouldBe null
        serverArtistId(compilation, "Adele") shouldBe "adele"
    }
}
