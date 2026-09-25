package com.simplecityapps.shuttle.ui.screens.library

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Route keys for the library detail screens, shared by every screen that links to them (library,
// search, Home, now playing). Keys only, never Parcelable models (app-shell.md, section 4). The album
// route is AlbumRoute in ui/shell/Routes.kt.

/** Mirrors AlbumArtistGroupKey. */
@Serializable
data class AlbumArtistRoute(
    val albumArtistKey: String?,
) : NavKey

@Serializable
data class GenreRoute(
    val genreName: String,
) : NavKey

@Serializable
data class PlaylistRoute(
    val playlistId: Long,
) : NavKey

/** One of the built-in smart playlists, by its stable id. */
@Serializable
data class SmartPlaylistRoute(
    val smartPlaylistId: String,
) : NavKey
