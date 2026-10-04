package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/**
 * An incremental sync's changed songs, plus each of [existingSongs] whose favourite the server now has otherwise,
 * carrying the server's state (#497). A server doesn't count a favourite toggled on it as a change to the song, so the
 * changed listing alone misses it; [favourites] is the server's whole favourites list, by song path, with the time each
 * became one. The library's merge decides what's stored (a toggle in the app not yet sent wins). Null [favourites] (the
 * fetch failed) leaves the listing as it is; the next sync fetches the whole list again.
 */
fun List<Song>.withFavouriteChanges(
    existingSongs: List<Song>,
    favourites: Map<String, Instant>?
): List<Song> {
    if (favourites == null) return this
    val changedPaths = mapTo(HashSet()) { song -> song.path }
    return this + existingSongs
        .filter { song -> song.path !in changedPaths && (song.favouritedAt != null) != (song.path in favourites) }
        .map { song -> song.copy(favouritedAt = favourites[song.path]) }
}
