package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder

/**
 * The name [AlbumsPage][com.simplecityapps.shuttle.ui.screens.library.AlbumsPage] indexes by first letter under
 * [sortOrder], the same one the sort compares; null for a sort that isn't by name, which keeps a plain thumb.
 */
fun albumLetterKey(sortOrder: AlbumSortOrder): ((Album) -> String?)? = when (sortOrder) {
    AlbumSortOrder.AlbumName, AlbumSortOrder.Default -> { album -> album.groupKey?.key }
    AlbumSortOrder.ArtistGroupKey -> { album -> album.groupKey?.albumArtistGroupKey?.key }
    else -> null
}

/** The plain thumb's popup under a sort that isn't by name: the year when sorted by year, else none. */
fun albumThumbLabel(sortOrder: AlbumSortOrder): ((Album) -> String?)? = if (sortOrder == AlbumSortOrder.Year) {
    { album -> album.year?.toString() }
} else {
    null
}
