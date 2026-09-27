package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlin.time.Instant

/**
 * The built-in smart playlists: each one's stable [id] and [SongQuery]. The app's source of truth for which
 * smart playlists exist. [id] is a slug (e.g. "recently-added"), not a list position or resource id, so a
 * saved back stack still resolves after the list is reordered; a display name comes from the platform side
 * (Android: `SmartPlaylistId.nameKey` in `LibraryRoutes.kt`).
 */
enum class SmartPlaylistId(
    val id: String,
    val songQuery: SongQuery,
) {
    /** Every favourite, most recently made one first (#497). */
    Favourites("favourites", SongQuery.Favourites),
    RecentlyAdded("recently-added", SongQuery.RecentlyAdded()),
    MostPlayed("most-played", SongQuery.PlayCount(2, SongSortOrder.PlayCount)),

    /** Every song that has played to the end, most recent first. */
    History("history", SongQuery.LastCompleted(Instant.fromEpochMilliseconds(0))),
    ;

    val smartPlaylist: SmartPlaylist get() = SmartPlaylist(this)

    companion object {
        fun fromId(id: String): SmartPlaylistId? = entries.firstOrNull { it.id == id }
    }
}
