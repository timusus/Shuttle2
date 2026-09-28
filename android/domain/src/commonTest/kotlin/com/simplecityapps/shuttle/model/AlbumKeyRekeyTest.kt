package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlbumKeyRekeyTest {
    private val songs = listOf(
        // An untagged album of several artists: before the rule each track artist was its own album
        AlbumIdentityTags(1, "Drive OST", null, null, listOf("Kavinsky"), null, null, null, MediaProviderType.Shuttle, "/music/Drive/1.mp3"),
        AlbumIdentityTags(2, "Drive OST", null, null, listOf("College"), null, null, null, MediaProviderType.Shuttle, "/music/Drive/2.mp3"),
        // A tagged album keyed by its MusicBrainz id
        AlbumIdentityTags(3, "Blue", "Joni Mitchell", null, listOf("Joni Mitchell"), null, "b1", null, MediaProviderType.Shuttle, "/music/Blue/1.mp3"),
        // An album whose key didn't change
        AlbumIdentityTags(4, "Low", "David Bowie", null, listOf("David Bowie"), null, null, null, MediaProviderType.Shuttle, "/music/Low/1.mp3")
    )

    private val rekey = AlbumKeyRekey(AlbumIdentityRule.resolve(songs).let { identities -> songs.map { it to identities.getValue(it.songId) } })

    private val drive = AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/music/Drive")

    @Test
    fun `an old key moves to the album its songs belong to now`() {
        rekey.album(AlbumGroupKey("drive ost", AlbumArtistGroupKey("kavinsky"))) shouldBe drive
        rekey.album(AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell"))) shouldBe AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell"), "mb:b1")
    }

    @Test
    fun `a current key is kept, and one no song had is null`() {
        rekey.album(AlbumGroupKey("low", AlbumArtistGroupKey("david bowie"))) shouldBe AlbumGroupKey("low", AlbumArtistGroupKey("david bowie"))
        rekey.album(drive) shouldBe drive
        rekey.album(AlbumGroupKey("gone", AlbumArtistGroupKey("nobody"))) shouldBe null
    }

    @Test
    fun `an old album artist key moves too`() {
        rekey.albumArtist(AlbumArtistGroupKey("college")) shouldBe AlbumArtistGroupKey("various artists")
        rekey.albumArtist(AlbumArtistGroupKey("joni mitchell")) shouldBe AlbumArtistGroupKey("joni mitchell")
    }

    @Test
    fun `a stored context moves, and other contexts are kept`() {
        val stored = PlayContext.decode(PlayContext.TYPE_ALBUM, "=kavinsky\u001F=drive ost")

        rekey.context(stored) shouldBe PlayContext.Album(drive)
        rekey.context(PlayContext.Genre("Jazz")) shouldBe PlayContext.Genre("Jazz")
        rekey.context(PlayContext.Album(AlbumGroupKey("gone", AlbumArtistGroupKey("nobody")))) shouldBe null
    }
}
