package com.simplecityapps.shuttle.ui.screens.library

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import kotlin.time.Instant
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

/**
 * The built-in smart playlists: each one's stable id, name and [SongQuery]. The app's source of truth for which
 * smart playlists exist; [of] matches a [SmartPlaylist] back to its entry by name.
 */
enum class SmartPlaylistId(
    val id: String,
    @StringRes val nameResId: Int,
    songQuery: SongQuery,
) {
    /** Every favourite, most recently made one first (#497). */
    Favourites("favourites", MediaProviderR.string.playlist_title_favorites, SongQuery.Favourites),
    RecentlyAdded("recently-added", MediaProviderR.string.playlist_title_recently_added, SongQuery.RecentlyAdded()),
    MostPlayed("most-played", MediaProviderR.string.playlist_title_most_played, SongQuery.PlayCount(2, SongSortOrder.PlayCount)),

    /** Every song that has played to the end, most recent first. */
    History("history", MediaProviderR.string.playlist_title_history, SongQuery.LastCompleted(Instant.fromEpochMilliseconds(0))),
    ;

    val smartPlaylist = SmartPlaylist(nameResId, songQuery)

    companion object {
        fun of(smartPlaylist: SmartPlaylist): SmartPlaylistId? = entries.firstOrNull { it.nameResId == smartPlaylist.nameResId }

        fun fromId(id: String): SmartPlaylistId? = entries.firstOrNull { it.id == id }
    }
}

val Album.route: AlbumRoute get() = AlbumRoute(albumKey = groupKey?.key, albumArtistKey = groupKey?.albumArtistGroupKey?.key)

val AlbumRoute.groupKey: AlbumGroupKey get() = AlbumGroupKey(albumKey, AlbumArtistGroupKey(albumArtistKey))

val AlbumArtist.route: AlbumArtistRoute get() = AlbumArtistRoute(groupKey.key)

val AlbumArtistRoute.groupKey: AlbumArtistGroupKey get() = AlbumArtistGroupKey(albumArtistKey)

fun SmartPlaylist.route(): SmartPlaylistRoute? = SmartPlaylistId.of(this)?.let { SmartPlaylistRoute(it.id) }
