package com.simplecityapps.shuttle.ui.screens.library

import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import com.simplecityapps.shuttle.ui.text.StringKey
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

/**
 * One of the built-in smart playlists, by its stable id: a [SmartPlaylistId.id] slug such as "recently-added".
 * Slugs, not list positions or string resource ids, so a saved back stack still resolves after the list is
 * reordered or the resources are renumbered.
 */
@Serializable
data class SmartPlaylistRoute(
    val smartPlaylistId: String,
) : NavKey

/** [SmartPlaylistId]'s display name. Display resolution is app-side; [SmartPlaylistId] itself is portable. */
val SmartPlaylistId.nameKey: StringKey
    get() = when (this) {
        SmartPlaylistId.Favourites -> StringKey.PLAYLIST_TITLE_FAVORITES
        SmartPlaylistId.RecentlyAdded -> StringKey.PLAYLIST_TITLE_RECENTLY_ADDED
        SmartPlaylistId.MostPlayed -> StringKey.PLAYLIST_TITLE_MOST_PLAYED
        SmartPlaylistId.History -> StringKey.PLAYLIST_TITLE_HISTORY
    }

val Album.route: AlbumRoute get() = AlbumRoute(albumKey = groupKey?.key, albumArtistKey = groupKey?.albumArtistGroupKey?.key)

val AlbumRoute.groupKey: AlbumGroupKey get() = AlbumGroupKey(albumKey, AlbumArtistGroupKey(albumArtistKey))

val AlbumArtist.route: AlbumArtistRoute get() = AlbumArtistRoute(groupKey.key)

val AlbumArtistRoute.groupKey: AlbumArtistGroupKey get() = AlbumArtistGroupKey(albumArtistKey)

fun SmartPlaylist.route(): SmartPlaylistRoute = SmartPlaylistRoute(id.id)
