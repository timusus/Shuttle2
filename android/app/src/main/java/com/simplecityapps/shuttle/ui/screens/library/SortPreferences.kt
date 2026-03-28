package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder

interface SortPreferences {
    var sortOrderSongList: SongSortOrder
    var sortOrderAlbumList: AlbumSortOrder
}
