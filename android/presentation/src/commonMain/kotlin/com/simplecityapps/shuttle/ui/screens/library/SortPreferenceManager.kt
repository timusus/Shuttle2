package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
class SortPreferenceManager @Inject constructor(private val store: KeyValueStore) : SortPreferences {
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

    override var sortOrderArtistList: AlbumArtistSortOrder
        set(value) {
            store.putString("sort_order_artist_list", value.name)
        }
        get() {
            return try {
                AlbumArtistSortOrder.valueOf(store.getString("sort_order_artist_list", AlbumArtistSortOrder.Default.name)!!)
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Failed to retrieve sort order" }
                AlbumArtistSortOrder.Default
            }
        }

    override var sortOrderArtistDetail: ArtistSongSortOrder
        set(value) {
            store.putString("sort_order_artist_detail", value.name)
        }
        get() {
            return try {
                ArtistSongSortOrder.valueOf(store.getString("sort_order_artist_detail", ArtistSongSortOrder.Default.name)!!)
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "Failed to retrieve sort order" }
                ArtistSongSortOrder.Default
            }
        }

    private companion object {
        val logger = Logger.tagged("SortPreferenceManager")
    }
}
