package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song

/**
 * Rewrites the source .m3u file of an m3u-imported playlist (its [Playlist.externalId]) after its songs change in S2,
 * so an external player sees the same edit. Best-effort: the file may have moved, lost its access grant since import, or
 * never been writable (one MediaStore lists outside every granted folder), so a failure is logged rather than surfaced;
 * the in-app playlist is still the source of truth, and the edit is remembered ([holdsUnwrittenEdits]) until a write
 * succeeds, so the next scan doesn't take it back.
 */
interface PlaylistFileSync {
    suspend fun write(
        playlist: Playlist,
        songs: List<Song>
    )

    /** Whether the playlist imported from [externalId] holds edits made in S2 that its file couldn't take. */
    fun holdsUnwrittenEdits(externalId: String): Boolean = false

    /**
     * The id the playlist file a stored [externalId] names has now: one stored under an older form of id (a shared storage
     * document URI, before playlists were known by their file path) gives the file path's id; any other is itself.
     */
    fun currentId(externalId: String): String = externalId

    /** Carries the unwritten-edits mark of a playlist whose id changes from [former] to [current] (see [currentId]). */
    fun moveUnwrittenMark(
        former: String,
        current: String
    ) = Unit

    companion object {
        /** For a platform that imports no playlist files, so has none to keep in sync (iOS, until it imports m3u files). */
        val None =
            object : PlaylistFileSync {
                override suspend fun write(
                    playlist: Playlist,
                    songs: List<Song>
                ) = Unit
            }
    }
}
