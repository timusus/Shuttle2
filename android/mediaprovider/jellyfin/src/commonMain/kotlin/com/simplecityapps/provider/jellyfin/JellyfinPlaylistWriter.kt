package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.ServerPlaylistWriter
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.toPlaylistWriteResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.PlaylistService
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/**
 * Writes the edits made in S2 to a Jellyfin playlist back to it (#916): `POST /Playlists/{id}/Items?ids=` adds, `DELETE
 * /Playlists/{id}/Items?entryIds=` removes, `POST /Playlists/{id}/Items/{entryId}/Move/{newIndex}` moves, `POST /Playlists/{id}`
 * renames and `DELETE /Items/{id}` deletes.
 */
class JellyfinPlaylistWriter
@Inject
constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val playlistService: PlaylistService
) : ServerPlaylistWriter {
    override val type = MediaProviderType.Jellyfin

    override suspend fun entries(playlistId: String): PlaylistWriteResult<List<ServerPlaylistEntry>> = request { address, credentials, authorization ->
        playlistService.entries(address, authorization, playlistId, credentials.userId)
    }.toPlaylistWriteResult { result ->
        result.items.mapNotNull { item -> item.playlistItemId?.let { entryId -> ServerPlaylistEntry(entryId, item.songPath) } }
    }

    override suspend fun add(
        playlistId: String,
        songPaths: List<String>
    ): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.add(address, authorization, playlistId, credentials.userId, songPaths.map { path -> path.removePrefix(SONG_PATH_PREFIX) })
    }.toPlaylistWriteResult()

    override suspend fun remove(
        playlistId: String,
        entryIds: List<String>
    ): PlaylistWriteResult<Unit> = request { address, _, authorization ->
        playlistService.remove(address, authorization, playlistId, entryIds)
    }.toPlaylistWriteResult()

    override suspend fun move(
        playlistId: String,
        entryId: String,
        index: Int,
        after: String?
    ): PlaylistWriteResult<Unit> = request { address, _, authorization ->
        playlistService.move(address, authorization, playlistId, entryId, index)
    }.toPlaylistWriteResult()

    override suspend fun rename(
        playlistId: String,
        name: String
    ): PlaylistWriteResult<Unit> = request { address, _, authorization ->
        playlistService.rename(address, authorization, playlistId, name)
    }.toPlaylistWriteResult()

    override suspend fun delete(playlistId: String): PlaylistWriteResult<Unit> = request { address, _, authorization ->
        playlistService.delete(address, authorization, playlistId)
    }.toPlaylistWriteResult()

    /** [block]'s request with the signed-in session, signing out if the server rejects it; a failure when signed out. */
    private suspend fun <T : Any> request(block: suspend (address: String, credentials: AuthenticatedCredentials, authorization: String) -> NetworkResult<T>): NetworkResult<T> {
        val address = authenticationManager.getAddress() ?: return NetworkResult.Failure(IllegalStateException("No Jellyfin address"))
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return NetworkResult.Failure(IllegalStateException("Not signed in to Jellyfin"))
        return authenticationManager.checkSession(credentials, block(address, credentials, authenticationManager.authorizationHeader(credentials)))
    }
}
