package com.simplecityapps.fakes

import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences

class FakeSortPreferences : SortPreferences {
    override var sortOrderSongList: SongSortOrder = SongSortOrder.Default
    override var sortOrderAlbumList: AlbumSortOrder = AlbumSortOrder.Default
}
