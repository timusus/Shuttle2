package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.albumartists.ArtistListPreferences
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListPreferences
import javax.inject.Inject

/** A library list's saved sort order or view mode, read with [ReadLibraryViewSetting] and saved with [SaveLibraryViewSetting]. */
sealed class LibraryViewSetting<T>(
    val read: LibraryViewPreferences.() -> T,
    val write: LibraryViewPreferences.(T) -> Unit,
) {
    data object SongSort : LibraryViewSetting<SongSortOrder>({ sort.sortOrderSongList }, { sort.sortOrderSongList = it })
    data object AlbumSort : LibraryViewSetting<AlbumSortOrder>({ sort.sortOrderAlbumList }, { sort.sortOrderAlbumList = it })
    data object PlaylistSort : LibraryViewSetting<PlaylistSortOrder>({ sort.sortOrderPlaylistList }, { sort.sortOrderPlaylistList = it })
    data object GenreSort : LibraryViewSetting<GenreSortOrder>({ sort.sortOrderGenreList }, { sort.sortOrderGenreList = it })
    data object AlbumViewMode : LibraryViewSetting<ViewMode>({ albumList.albumListViewMode }, { albumList.albumListViewMode = it })
    data object ArtistViewMode : LibraryViewSetting<ViewMode>({ artistList.artistListViewMode }, { artistList.artistListViewMode = it })
}

/** The preference holders behind [LibraryViewSetting], for the two use cases to share. */
class LibraryViewPreferences @Inject constructor(
    val sort: SortPreferences,
    val albumList: AlbumListPreferences,
    val artistList: ArtistListPreferences,
)

/** The saved value of one library list [LibraryViewSetting]. */
class ReadLibraryViewSetting @Inject constructor(
    private val preferences: LibraryViewPreferences,
) {
    operator fun <T> invoke(setting: LibraryViewSetting<T>): T = setting.read(preferences)
}

/** Saves [value] as one library list's [LibraryViewSetting]. */
class SaveLibraryViewSetting @Inject constructor(
    private val preferences: LibraryViewPreferences,
) {
    operator fun <T> invoke(setting: LibraryViewSetting<T>, value: T) = setting.write(preferences, value)
}
