package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.ServerPlaylistWriter
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServer
import com.simplecityapps.mediaprovider.server.toPlaylistWriteResult
import com.simplecityapps.mediaprovider.then
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.PlaylistService
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the edits made in S2 to an Emby playlist back to it (#916): `POST /Playlists/{id}/Items?ids=` adds, `DELETE
 * /Playlists/{id}/Items?entryIds=` removes, `POST /Playlists/{id}/Items/{entryId}/Move/{newIndex}` moves, `POST /Items/{id}`
 * renames and `DELETE /Items/{id}` deletes.
 */
class EmbyPlaylistWriter
@Inject
constructor(
    private val authenticationManager: EmbyAuthenticationManager,
    private val playlistService: PlaylistService
) : ServerPlaylistWriter {
    override val type = MediaProviderType.Emby

    override suspend fun entries(playlistId: String): PlaylistWriteResult<List<ServerPlaylistEntry>> = request { address, credentials, authorization ->
        playlistService.entries(address, credentials.accessToken, authorization, playlistId, credentials.userId)
    }.toPlaylistWriteResult { result ->
        result.items.mapNotNull { item -> item.playlistItemId?.let { entryId -> ServerPlaylistEntry(entryId, MediaBrowserServer.Emby.songPath(item)) } }
    }

    override suspend fun add(
        playlistId: String,
        songPaths: List<String>
    ): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.add(address, credentials.accessToken, authorization, playlistId, credentials.userId, songPaths.map { path -> path.removePrefix(MediaBrowserServer.Emby.songPathPrefix) })
    }.toPlaylistWriteResult()

    override suspend fun remove(
        playlistId: String,
        entryIds: List<String>
    ): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.remove(address, credentials.accessToken, authorization, playlistId, entryIds)
    }.toPlaylistWriteResult()

    override suspend fun move(
        playlistId: String,
        entryId: String,
        index: Int,
        after: String?
    ): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.move(address, credentials.accessToken, authorization, playlistId, entryId, index)
    }.toPlaylistWriteResult()

    /** Emby has no rename: its item update takes the whole item, so the playlist's item is read and sent back with the new name. */
    override suspend fun rename(
        playlistId: String,
        name: String
    ): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.item(address, credentials.accessToken, authorization, playlistId, credentials.userId)
    }.toPlaylistWriteResult { item -> item }.then { item ->
        request { address, credentials, authorization ->
            playlistService.update(address, credentials.accessToken, authorization, playlistId, JsonObject(item + ("Name" to JsonPrimitive(name))))
        }.toPlaylistWriteResult()
    }

    override suspend fun delete(playlistId: String): PlaylistWriteResult<Unit> = request { address, credentials, authorization ->
        playlistService.delete(address, credentials.accessToken, authorization, playlistId)
    }.toPlaylistWriteResult()

    /** [block]'s request with the signed-in session, signing out if the server rejects it; a failure when signed out. */
    private suspend fun <T : Any> request(block: suspend (address: String, credentials: AuthenticatedCredentials, authorization: String) -> NetworkResult<T>): NetworkResult<T> {
        val address = authenticationManager.getAddress() ?: return NetworkResult.Failure(IllegalStateException("No Emby address"))
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return NetworkResult.Failure(IllegalStateException("Not signed in to Emby"))
        return authenticationManager.checkSession(credentials, block(address, credentials, authenticationManager.clientAuthorizationHeader()))
    }
}
