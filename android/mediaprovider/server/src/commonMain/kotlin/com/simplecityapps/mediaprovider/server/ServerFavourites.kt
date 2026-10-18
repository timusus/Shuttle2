package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/** A server doesn't mark a song changed when its favourite is toggled, so the changed listing alone misses it; the whole favourites list catches it. */
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
