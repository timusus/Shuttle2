package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Keys across the local artist split of #880: the songs before it hold the whole ARTIST tag, after it its artists. */
class AlbumKeyChangeTest {
    private fun song(
        id: Long,
        album: String,
        artists: List<String>,
        path: String,
        albumArtist: String? = null
    ) = AlbumIdentityTags(id, album, albumArtist, null, artists, null, null, null, MediaProviderType.Shuttle, path)

    private val before = listOf(
        // An untagged album whose ARTIST names two artists
        song(1, "Duets", listOf("Ann / Bob"), "/music/Duets/1.mp3"),
        song(2, "Duets", listOf("Ann / Bob"), "/music/Duets/2.mp3"),
        // An untagged folder that read as several artists, and reads as one once split
        song(3, "Mixed", listOf("Cat | Dan"), "/music/Mixed/1.mp3"),
        song(4, "Mixed", listOf("Cat"), "/music/Mixed/2.mp3"),
        // An album the split doesn't touch
        song(5, "Low", listOf("David Bowie"), "/music/Low/1.mp3", albumArtist = "David Bowie")
    )

    private val after = before.map { song -> song.copy(artists = song.artists.flatMap { it.split(" / ", " | ") }) }

    private val change = AlbumKeyChange(AlbumIndex(before), AlbumIndex(after))

    private val low = AlbumGroupKey("low", AlbumArtistGroupKey("david bowie"))

    @Test
    fun `an album its songs left moves to the album they belong to now`() {
        change.album(AlbumGroupKey("duets", AlbumArtistGroupKey("ann / bob"))) shouldBe AlbumGroupKey("duets", AlbumArtistGroupKey("ann"))
        change.album(AlbumGroupKey("mixed", AlbumArtistGroupKey("various artists"), "dir:/music/Mixed")) shouldBe AlbumGroupKey("mixed", AlbumArtistGroupKey("cat"))
    }

    @Test
    fun `a key the change leaves alone is kept and one no song had is null`() {
        change.album(low) shouldBe low
        change.album(AlbumGroupKey("duets", AlbumArtistGroupKey("ann"))) shouldBe AlbumGroupKey("duets", AlbumArtistGroupKey("ann"))
        change.album(AlbumGroupKey("gone", AlbumArtistGroupKey("nobody"))) shouldBe null
    }

    @Test
    fun `an album artist moves to the album artist its songs have now and one still credited is kept`() {
        change.albumArtist(AlbumArtistGroupKey("ann / bob")) shouldBe AlbumArtistGroupKey("ann")
        change.albumArtist(AlbumArtistGroupKey("bob")) shouldBe AlbumArtistGroupKey("bob")
        change.albumArtist(AlbumArtistGroupKey("nobody")) shouldBe null
    }

    @Test
    fun `contexts move with their album or album artist and others pass through`() {
        change.context(PlayContext.Album(AlbumGroupKey("duets", AlbumArtistGroupKey("ann / bob")))) shouldBe PlayContext.Album(AlbumGroupKey("duets", AlbumArtistGroupKey("ann")))
        change.context(PlayContext.AlbumArtist(AlbumArtistGroupKey("ann / bob"))) shouldBe PlayContext.AlbumArtist(AlbumArtistGroupKey("ann"))
        change.context(PlayContext.Genre("Jazz")) shouldBe PlayContext.Genre("Jazz")
    }
}
