package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The keys and defaults [SortPreferenceManager] saves under, pinned so a saved sort order survives (#584). */
class SortPreferenceManagerTest {
    private val store = InMemoryKeyValueStore()
    private val sortPreferences = SortPreferenceManager(store)

    @Test
    fun `nothing saved reads as the defaults`() {
        sortPreferences.sortOrderSongList shouldBe SongSortOrder.SongName
        sortPreferences.sortOrderAlbumList shouldBe AlbumSortOrder.AlbumName
        sortPreferences.sortOrderPlaylistList shouldBe PlaylistSortOrder.Default
        sortPreferences.sortOrderGenreList shouldBe GenreSortOrder.Default
        sortPreferences.sortOrderArtistDetail shouldBe ArtistSongSortOrder.AlbumNewest
    }

    @Test
    fun `each sort order is saved by name under its key`() {
        sortPreferences.sortOrderSongList = SongSortOrder.entries.last()
        sortPreferences.sortOrderAlbumList = AlbumSortOrder.entries.last()
        sortPreferences.sortOrderPlaylistList = PlaylistSortOrder.Name
        sortPreferences.sortOrderGenreList = GenreSortOrder.entries.last()
        sortPreferences.sortOrderArtistDetail = ArtistSongSortOrder.MostPlayed

        store.values shouldBe mapOf(
            "sort_order_song_list" to SongSortOrder.entries.last().name,
            "sort_order_album_list" to AlbumSortOrder.entries.last().name,
            "sort_order_playlist_list" to "Name",
            "sort_order_genre_list" to GenreSortOrder.entries.last().name,
            "sort_order_artist_detail" to "MostPlayed"
        )
    }

    @Test
    fun `a name no sort order has any more reads as the default`() {
        val saved = InMemoryKeyValueStore(
            mapOf(
                "sort_order_song_list" to "Gone",
                "sort_order_album_list" to "Gone",
                "sort_order_playlist_list" to "Gone",
                "sort_order_genre_list" to "Gone",
                "sort_order_artist_detail" to "Gone"
            )
        )

        val sortPreferences = SortPreferenceManager(saved)

        sortPreferences.sortOrderSongList shouldBe SongSortOrder.SongName
        sortPreferences.sortOrderAlbumList shouldBe AlbumSortOrder.AlbumName
        sortPreferences.sortOrderPlaylistList shouldBe PlaylistSortOrder.Default
        sortPreferences.sortOrderGenreList shouldBe GenreSortOrder.Default
        sortPreferences.sortOrderArtistDetail shouldBe ArtistSongSortOrder.AlbumNewest
    }
}
