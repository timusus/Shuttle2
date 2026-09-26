package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.compose.runtime.Composable
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import java.util.Locale

/** Fast-scroll popup text and overlay for [AlbumsPage][com.simplecityapps.shuttle.ui.screens.library.AlbumsPage], keyed by the current sort order. */
fun getAlbumPopupText(album: Album, sortOrder: AlbumSortOrder): String = when (sortOrder) {
    AlbumSortOrder.AlbumName,
    AlbumSortOrder.Default -> album.groupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())

    AlbumSortOrder.ArtistGroupKey -> album.groupKey?.albumArtistGroupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())

    AlbumSortOrder.Year -> album.year?.toString()

    else -> null
} ?: ""

fun getAlbumFastscrollPopup(sortOrder: AlbumSortOrder): @Composable ((Int) -> Unit)? = when (sortOrder) {
    // Leave the default popup for these cases
    AlbumSortOrder.AlbumName,
    AlbumSortOrder.ArtistGroupKey,
    AlbumSortOrder.Year,
    AlbumSortOrder.Default -> null

    // The rest of sort orders don't have a meaningful popup
    else -> ::NoPopup
}
