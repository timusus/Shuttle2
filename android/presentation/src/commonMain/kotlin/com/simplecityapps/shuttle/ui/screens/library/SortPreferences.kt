package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSortOrder

interface SortPreferences {
    var sortOrderSongList: SongSortOrder
    var sortOrderAlbumList: AlbumSortOrder
    var sortOrderPlaylistList: PlaylistSortOrder
    var sortOrderGenreList: GenreSortOrder
    var sortOrderArtistList: AlbumArtistSortOrder

    /** How an artist's detail screen lists its songs, the same for every artist. */
    var sortOrderArtistDetail: ArtistSongSortOrder

    /** One playlist's chosen sort, saved under its [com.simplecityapps.shuttle.sorting.PlaylistSortKey]; null until chosen. */
    fun playlistSort(key: String): SongListSort?

    fun setPlaylistSort(key: String, sort: SongListSort)
}
