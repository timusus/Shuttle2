package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PlayContextTest {
    @Test
    fun `every context reads back from its type and id`() {
        listOf(
            PlayContext.Album(AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell"))),
            PlayContext.Album(AlbumGroupKey(null, AlbumArtistGroupKey(null))),
            PlayContext.Album(AlbumGroupKey("", AlbumArtistGroupKey("x"))),
            PlayContext.AlbumArtist(AlbumArtistGroupKey("radiohead")),
            PlayContext.AlbumArtist(AlbumArtistGroupKey(null)),
            PlayContext.Playlist(42),
            PlayContext.SmartPlaylist(SmartPlaylistId.MostPlayed),
            PlayContext.UserSmartPlaylist(3),
            PlayContext.Genre("Jazz"),
            PlayContext.None
        ).forEach { context ->
            PlayContext.decode(context.type, context.id) shouldBe context
        }
    }

    @Test
    fun `an album with no artist key reads back as one`() {
        val context = PlayContext.Album(AlbumGroupKey("blue", null))

        PlayContext.decode(context.type, context.id) shouldBe PlayContext.Album(AlbumGroupKey("blue", AlbumArtistGroupKey(null)))
    }

    @Test
    fun `an unknown type or a bad id reads back as none`() {
        PlayContext.decode("podcast", "1") shouldBe PlayContext.None
        PlayContext.decode(PlayContext.TYPE_PLAYLIST, "x") shouldBe PlayContext.None
        PlayContext.decode(PlayContext.TYPE_SMART_PLAYLIST, "gone") shouldBe PlayContext.None
        PlayContext.decode(null, null) shouldBe PlayContext.None
    }
}
