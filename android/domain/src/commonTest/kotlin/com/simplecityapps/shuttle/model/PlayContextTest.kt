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
    fun `an album keyed by an identity reads back with it`() {
        val context = PlayContext.Album(AlbumGroupKey("blue", AlbumArtistGroupKey("various artists"), "dir:/music/Blue"))

        PlayContext.decode(context.type, context.id) shouldBe context
    }

    @Test
    fun `an album id stored before the identity rule reads back as a name-rule key`() {
        // As #633 stored it: the artist key then the album key, each "=" and its value, joined by a unit separator
        PlayContext.decode(PlayContext.TYPE_ALBUM, "=joni mitchell\u001F=blue") shouldBe PlayContext.Album(AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell")))
    }

    @Test
    fun `an unknown type or a bad id reads back as none`() {
        PlayContext.decode("podcast", "1") shouldBe PlayContext.None
        PlayContext.decode(PlayContext.TYPE_PLAYLIST, "x") shouldBe PlayContext.None
        PlayContext.decode(PlayContext.TYPE_SMART_PLAYLIST, "gone") shouldBe PlayContext.None
        PlayContext.decode(null, null) shouldBe PlayContext.None
    }
}
