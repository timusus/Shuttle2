package com.simplecityapps.shuttle.compose.ui.main.library

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.compose.R

enum class LibraryTab {
    Genres, Playlists, Artists, Albums, Songs
}

@StringRes
fun LibraryTab.nameResId(): Int {
    return when (this) {
        LibraryTab.Genres -> R.string.genres
        LibraryTab.Playlists -> R.string.library_playlists
        LibraryTab.Artists -> R.string.artists
        LibraryTab.Albums -> R.string.albums
        LibraryTab.Songs -> R.string.songs
    }
}
