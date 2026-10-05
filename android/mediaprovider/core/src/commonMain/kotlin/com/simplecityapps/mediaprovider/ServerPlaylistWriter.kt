package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * Writes the edits made in S2 to a playlist imported from [type]'s server back to that server (#916), each call one request
 * (or a few) that [ServerPlaylistSync] makes as it sends its queue. Playlists and songs are named as the import names them:
 * a playlist by its [MediaImporter.PlaylistUpdateData.externalId], a song by its path.
 */
interface ServerPlaylistWriter {
    val type: MediaProviderType

    /** The playlist's entries on the server as they are now, in order. */
    suspend fun entries(playlistId: String): PlaylistWriteResult<List<ServerPlaylistEntry>>

    /** Adds the songs at [songPaths] to the end of the playlist, in that order. */
    suspend fun add(
        playlistId: String,
        songPaths: List<String>
    ): PlaylistWriteResult<Unit>

    /** Removes the entries with [entryIds] from the playlist. */
    suspend fun remove(
        playlistId: String,
        entryIds: List<String>
    ): PlaylistWriteResult<Unit>

    /** Moves the entry [entryId] to [index] in the playlist, which puts it straight after the entry [after] (null: first). */
    suspend fun move(
        playlistId: String,
        entryId: String,
        index: Int,
        after: String?
    ): PlaylistWriteResult<Unit>
}

/** One entry of a server's playlist: the server's own id for it, which a removal or a move names, and its song's path. */
data class ServerPlaylistEntry(
    val entryId: String,
    val songPath: String
)

/** How a request to a server's playlist went. */
sealed interface PlaylistWriteResult<out T> {
    data class Success<T>(val value: T) : PlaylistWriteResult<T>

    /** Not done now (no connection, a server error, signed out): the edit is sent again later. */
    data object Failed : PlaylistWriteResult<Nothing>

    /** The server has no such playlist any more: no edit to it can be sent. */
    data object PlaylistGone : PlaylistWriteResult<Nothing>

    /** The server refused the request (any other client error, such as a playlist the user can't edit): sending it again won't change that. */
    data object Refused : PlaylistWriteResult<Nothing>
}

/** [block] of this result's value, or how it failed. */
inline fun <T, R> PlaylistWriteResult<T>.then(block: (T) -> PlaylistWriteResult<R>): PlaylistWriteResult<R> = when (this) {
    is PlaylistWriteResult.Success -> block(value)
    PlaylistWriteResult.Failed -> PlaylistWriteResult.Failed
    PlaylistWriteResult.PlaylistGone -> PlaylistWriteResult.PlaylistGone
    PlaylistWriteResult.Refused -> PlaylistWriteResult.Refused
}
