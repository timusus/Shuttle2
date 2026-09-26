package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.runtime.Composable
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import java.util.Locale

/** Fast-scroll popup text and overlay for [SongsPage][com.simplecityapps.shuttle.ui.screens.library.SongsPage], keyed by the current sort order. */
fun getFastscrollPopupText(song: Song, sortOrder: SongSortOrder): String = when (sortOrder) {
    SongSortOrder.SongName -> song.name?.firstOrNull()?.toString()
    SongSortOrder.ArtistGroupKey -> song.albumArtistGroupKey.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    SongSortOrder.AlbumGroupKey -> song.albumGroupKey.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    SongSortOrder.Year -> song.date?.year?.toString()
    else -> null
} ?: ""

fun getFastscrollPopup(sortOrder: SongSortOrder): @Composable ((Int) -> Unit)? = when (sortOrder) {
    // Leave the default popup for these cases
    SongSortOrder.SongName,
    SongSortOrder.ArtistGroupKey,
    SongSortOrder.AlbumGroupKey,
    SongSortOrder.Year -> null

    // Don't show popup in these cases
    SongSortOrder.LastModified,
    SongSortOrder.Duration -> ::NoPopup

    // The rest of sort orders aren't available in the menu
    else -> null
}
