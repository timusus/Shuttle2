package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder

/**
 * The plain thumb's popup under a sort that isn't by name: the year when sorted by year, else none. A name sort
 * indexes by letter instead ([songLetterKey][com.simplecityapps.shuttle.sorting.songLetterKey]).
 */
fun songThumbLabel(sortOrder: SongSortOrder): ((Song) -> String?)? = if (sortOrder == SongSortOrder.Year) {
    { song -> song.date?.year?.toString() }
} else {
    null
}
