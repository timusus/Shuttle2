package com.simplecityapps.shuttle.ui.screens.library

import android.content.SharedPreferences
import com.simplecityapps.shuttle.persistence.get
import com.simplecityapps.shuttle.persistence.put
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import timber.log.Timber

class SortPreferenceManager(private val sharedPreferences: SharedPreferences) {

    var sortOrderSongList: SongSortOrder
        set(value) {
            sharedPreferences.put("sort_order_song_list", value.name)
        }
        get() {
            return try {
                SongSortOrder.valueOf(sharedPreferences.get("sort_order_song_list", SongSortOrder.SongName.name))
            } catch (e: IllegalArgumentException) {
                SongSortOrder.SongName
            }
        }

    var sortOrderAlbumList: AlbumSortOrder
        set(value) {
            sharedPreferences.put("sort_order_album_list", value.name)
        }
        get() {
            return try {
                AlbumSortOrder.valueOf(sharedPreferences.get("sort_order_album_list", AlbumSortOrder.AlbumName.name))
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to retrieve sort order")
                AlbumSortOrder.AlbumName
            }
        }
}