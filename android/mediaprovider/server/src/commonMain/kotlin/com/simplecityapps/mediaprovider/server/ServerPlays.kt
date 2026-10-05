package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.Song

/**
 * An incremental sync's changed listing plus the songs [played] on the server since the last sync: a play made in another
 * client doesn't change a song's saved or updated date, so the changed listing alone misses it. A song in both is taken
 * from the listing (they're read alike); the library's merge keeps the higher play count and the later last played.
 * Null [played] (the fetch failed) leaves the listing as it is.
 */
fun List<Song>.withPlayedSongs(played: List<Song>?): List<Song> {
    if (played.isNullOrEmpty()) return this
    val listed = mapTo(HashSet()) { song -> song.path }
    return this + played.filter { song -> song.path !in listed }
}
