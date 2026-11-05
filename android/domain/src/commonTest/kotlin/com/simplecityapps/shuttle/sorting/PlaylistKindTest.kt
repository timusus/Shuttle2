package com.simplecityapps.shuttle.sorting

import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumNewest
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumNewestAdded
import com.simplecityapps.shuttle.sorting.SongListSort.AlbumTitle
import com.simplecityapps.shuttle.sorting.SongListSort.Artist
import com.simplecityapps.shuttle.sorting.SongListSort.MostPlayed
import com.simplecityapps.shuttle.sorting.SongListSort.NewestAdded
import com.simplecityapps.shuttle.sorting.SongListSort.PlaylistOrder
import com.simplecityapps.shuttle.sorting.SongListSort.SongTitle
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PlaylistKindTest {
    @Test
    fun `recently added opens grouped by newest added album with flat newest added and title and artist`() {
        PlaylistKind.RecentlyAdded.defaultSort shouldBe AlbumNewestAdded
        PlaylistKind.RecentlyAdded.sorts shouldBe listOf(AlbumNewestAdded, AlbumTitle, AlbumNewest, NewestAdded, SongTitle, Artist)
    }

    @Test
    fun `downloaded opens grouped A to Z with flat options`() {
        PlaylistKind.Downloaded.defaultSort shouldBe AlbumTitle
        PlaylistKind.Downloaded.sorts shouldBe listOf(AlbumNewestAdded, AlbumTitle, AlbumNewest, NewestAdded, SongTitle, Artist)
    }

    @Test
    fun `favourites opens flat in favourited order with grouping optional`() {
        PlaylistKind.Favourites.defaultSort shouldBe PlaylistOrder
        PlaylistKind.Favourites.sorts shouldBe listOf(PlaylistOrder, SongTitle, Artist, AlbumNewestAdded, AlbumTitle, AlbumNewest)
    }

    @Test
    fun `most played opens by play count and never groups`() {
        PlaylistKind.MostPlayed.defaultSort shouldBe MostPlayed
        PlaylistKind.MostPlayed.sorts shouldBe listOf(MostPlayed, SongTitle, Artist)
        PlaylistKind.MostPlayed.offersGrouping shouldBe false
    }

    @Test
    fun `history opens most recent first and never groups`() {
        PlaylistKind.History.defaultSort shouldBe PlaylistOrder
        PlaylistKind.History.sorts shouldBe listOf(PlaylistOrder, SongTitle, Artist)
        PlaylistKind.History.offersGrouping shouldBe false
    }

    @Test
    fun `user smart and regular playlists open in their own order with every sort and grouping on offer`() {
        val all = listOf(PlaylistOrder, NewestAdded, SongTitle, Artist, MostPlayed, AlbumNewestAdded, AlbumTitle, AlbumNewest)
        PlaylistKind.UserSmart.defaultSort shouldBe PlaylistOrder
        PlaylistKind.UserSmart.sorts shouldBe all
        PlaylistKind.User.defaultSort shouldBe PlaylistOrder
        PlaylistKind.User.sorts shouldBe all
    }

    @Test
    fun `every default is on offer`() {
        PlaylistKind.entries.filter { it.defaultSort !in it.sorts } shouldBe emptyList()
    }

    @Test
    fun `a saved sort the kind no longer offers falls back to its default`() {
        PlaylistKind.History.resolve(AlbumTitle) shouldBe PlaylistOrder
        PlaylistKind.History.resolve(SongTitle) shouldBe SongTitle
        PlaylistKind.Downloaded.resolve(null) shouldBe AlbumTitle
    }

    @Test
    fun `each built-in smart playlist maps to its kind`() {
        SmartPlaylistId.entries.associateWith { PlaylistKind.of(it) } shouldBe mapOf(
            SmartPlaylistId.Favourites to PlaylistKind.Favourites,
            SmartPlaylistId.RecentlyAdded to PlaylistKind.RecentlyAdded,
            SmartPlaylistId.RecentReleases to PlaylistKind.RecentReleases,
            SmartPlaylistId.MostPlayed to PlaylistKind.MostPlayed,
            SmartPlaylistId.History to PlaylistKind.History,
            SmartPlaylistId.Downloaded to PlaylistKind.Downloaded,
        )
    }

    @Test
    fun `each playlist saves its sort under its own key`() {
        PlaylistSortKey.of(SmartPlaylistId.RecentlyAdded) shouldBe "playlist_sort_recently-added"
        PlaylistSortKey.user(7) shouldBe "playlist_sort_user_7"
        PlaylistSortKey.userSmart(7) shouldBe "playlist_sort_smart_7"
    }
}
