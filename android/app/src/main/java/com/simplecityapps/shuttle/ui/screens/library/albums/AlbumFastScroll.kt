package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder

/**
 * The plain thumb's popup under a sort that isn't by name: the year when sorted by year, else none. A name sort
 * indexes by letter instead ([albumLetterKey][com.simplecityapps.shuttle.sorting.albumLetterKey]).
 */
fun albumThumbLabel(sortOrder: AlbumSortOrder): ((Album) -> String?)? = if (sortOrder == AlbumSortOrder.Year) {
    { album -> album.year?.toString() }
} else {
    null
}
