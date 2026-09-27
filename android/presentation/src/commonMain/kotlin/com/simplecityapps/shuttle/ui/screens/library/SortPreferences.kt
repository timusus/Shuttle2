package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder

interface SortPreferences {
    var sortOrderSongList: SongSortOrder
    var sortOrderAlbumList: AlbumSortOrder
    var sortOrderPlaylistList: PlaylistSortOrder
    var sortOrderGenreList: GenreSortOrder
}
