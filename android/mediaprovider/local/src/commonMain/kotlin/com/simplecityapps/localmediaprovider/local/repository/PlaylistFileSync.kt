package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song

/**
 * Rewrites the source .m3u file of an m3u-imported playlist (its [Playlist.externalId]) after its songs change in S2,
 * so an external player sees the same edit. Best-effort: the file may have moved or lost its access grant since import,
 * so a failure is logged rather than surfaced; the in-app playlist is still the source of truth.
 */
fun interface PlaylistFileSync {
    suspend fun write(
        playlist: Playlist,
        songs: List<Song>
    )

    companion object {
        /** For a platform that imports no playlist files, so has none to keep in sync (iOS, until it imports m3u files). */
        val None = PlaylistFileSync { _, _ -> }
    }
}
