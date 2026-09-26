package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder

/**
 * The name [SongsPage][com.simplecityapps.shuttle.ui.screens.library.SongsPage] indexes by first letter under
 * [sortOrder], the same one the sort compares; null for a sort that isn't by name, which keeps a plain thumb.
 */
fun songLetterKey(sortOrder: SongSortOrder): ((Song) -> String?)? = when (sortOrder) {
    SongSortOrder.SongName -> { song -> song.name }
    SongSortOrder.ArtistGroupKey -> { song -> song.albumArtistGroupKey.key }
    SongSortOrder.AlbumGroupKey, SongSortOrder.Default -> { song -> song.albumGroupKey.key }
    else -> null
}

/** The plain thumb's popup under a sort that isn't by name: the year when sorted by year, else none. */
fun songThumbLabel(sortOrder: SongSortOrder): ((Song) -> String?)? = if (sortOrder == SongSortOrder.Year) {
    { song -> song.date?.year?.toString() }
} else {
    null
}
