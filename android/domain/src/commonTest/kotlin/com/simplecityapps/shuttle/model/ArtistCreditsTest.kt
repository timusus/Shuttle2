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
    fun `a combined album artist tag credits each of its artists`() {
        val credits = credits(tags(album = "Duets", artists = listOf("A"), albumArtist = "A; B"))

        credits.map { it.groupKey } shouldBe listOf(key("A"), key("B"))
    }

    @Test
    fun `an album artist with one artist adds no credit`() {
        val credits = credits(tags(album = "Solo", artists = listOf("A"), albumArtist = "AC/DC"))

        credits.map { it.groupKey } shouldBe listOf(key("A"))
    }

    @Test
    fun `splitMultiArtist splits on semicolons pipes and spaced slashes`() {
        splitMultiArtist("A;B | C / D") shouldBe listOf("A", "B", "C", "D")
        splitMultiArtist("AC/DC") shouldBe listOf("AC/DC")
        splitMultiArtist("Earth, Wind & Fire") shouldBe listOf("Earth, Wind & Fire")
        splitMultiArtist(" ; A ;a; ") shouldBe listOf("A")
    }
}
