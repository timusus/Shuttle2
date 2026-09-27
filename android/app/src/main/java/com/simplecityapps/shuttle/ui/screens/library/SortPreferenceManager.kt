package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder

class SortPreferenceManager(private val store: KeyValueStore) : SortPreferences {
    override var sortOrderSongList: SongSortOrder
        set(value) {
            store.putString("sort_order_song_list", value.name)
        }
        get() {
            return try {
                SongSortOrder.valueOf(store.getString("sort_order_song_list", SongSortOrder.SongName.name)!!)
            } catch (e: IllegalArgumentException) {
                SongSortOrder.SongName
            }
        }

    override var sortOrderAlbumList: AlbumSortOrder
        set(value) {
            store.putString("sort_order_album_list", value.name)
        }
        get() {
            return try {
                AlbumSortOrder.valueOf(store.getString("sort_order_album_list", AlbumSortOrder.AlbumName.name)!!)
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Failed to retrieve sort order" }
                AlbumSortOrder.AlbumName
            }
        }

    override var sortOrderPlaylistList: PlaylistSortOrder
        set(value) {
            store.putString("sort_order_playlist_list", value.name)
        }
        get() {
            return try {
                PlaylistSortOrder.valueOf(store.getString("sort_order_playlist_list", PlaylistSortOrder.Default.name)!!)
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Failed to retrieve sort order" }
                PlaylistSortOrder.Default
            }
        }

    override var sortOrderGenreList: GenreSortOrder
        set(value) {
            store.putString("sort_order_genre_list", value.name)
        }
        get() {
            return try {
                GenreSortOrder.valueOf(store.getString("sort_order_genre_list", GenreSortOrder.Default.name)!!)
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Failed to retrieve sort order" }
                GenreSortOrder.Default
            }
        }

    private companion object {
        val logger = Logger.tagged("SortPreferenceManager")
    }
}
