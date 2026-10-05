package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.ServerPlaylistWriter
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.toPlaylistWriteResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.PlaylistService
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/**
 * Writes the edits made in S2 to a Plex playlist back to it (#916): `PUT /playlists/{id}/items?uri=server://{machineId}/...`
 * adds, `DELETE /playlists/{id}/items/{playlistItemID}` removes one entry, `PUT /playlists/{id}/items/{playlistItemID}/move?after=`
 * moves one, `PUT /playlists/{id}?title=` renames and `DELETE /playlists/{id}` deletes. The machine id is the server's client identifier, which sign-in keeps as the credentials' user id.
 */
class PlexPlaylistWriter
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val playlistService: PlaylistService
) : ServerPlaylistWriter {
    override val type = MediaProviderType.Plex

    override suspend fun entries(playlistId: String): PlaylistWriteResult<List<ServerPlaylistEntry>> = request { address, credentials ->
        playlistService.entries(address, credentials.accessToken, playlistId)
    }.toPlaylistWriteResult { result ->
        result.mediaContainer.metadata.orEmpty().mapNotNull { metadata -> metadata.playlistItemId?.let { entryId -> ServerPlaylistEntry(entryId.toString(), metadata.songPath) } }
    }

    override suspend fun add(
        playlistId: String,
        songPaths: List<String>
    ): PlaylistWriteResult<Unit> {
        val ratingKeys = songPaths.mapNotNull(::plexRatingKey)
        if (ratingKeys.isEmpty()) {
            return PlaylistWriteResult.Success(Unit)
        }
        return request { address, credentials ->
            val uri = "server://${credentials.userId}/$LIBRARY_IDENTIFIER$METADATA_PATH${ratingKeys.joinToString(",")}"
            playlistService.add(address, credentials.accessToken, playlistId, uri)
        }.toPlaylistWriteResult()
    }

    /** One request per entry: Plex removes a single entry at a time. */
    override suspend fun remove(
        playlistId: String,
        entryIds: List<String>
    ): PlaylistWriteResult<Unit> {
        entryIds.forEach { entryId ->
            val result = request { address, credentials -> playlistService.remove(address, credentials.accessToken, playlistId, entryId) }.toPlaylistWriteResult()
            if (result !is PlaylistWriteResult.Success) {
                return result
            }
        }
        return PlaylistWriteResult.Success(Unit)
    }

    override suspend fun move(
        playlistId: String,
        entryId: String,
        index: Int,
        after: String?
    ): PlaylistWriteResult<Unit> = request { address, credentials ->
        playlistService.move(address, credentials.accessToken, playlistId, entryId, after)
    }.toPlaylistWriteResult()

    override suspend fun rename(
        playlistId: String,
        name: String
    ): PlaylistWriteResult<Unit> = request { address, credentials ->
        playlistService.rename(address, credentials.accessToken, playlistId, name)
    }.toPlaylistWriteResult()

    override suspend fun delete(playlistId: String): PlaylistWriteResult<Unit> = request { address, credentials ->
        playlistService.delete(address, credentials.accessToken, playlistId)
    }.toPlaylistWriteResult()

    /** [block]'s request with the signed-in session, signing out if the server rejects it; a failure when signed out. */
    private suspend fun <T : Any> request(block: suspend (address: String, credentials: AuthenticatedCredentials) -> NetworkResult<T>): NetworkResult<T> {
        val address = authenticationManager.getAddress() ?: return NetworkResult.Failure(IllegalStateException("No Plex address"))
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return NetworkResult.Failure(IllegalStateException("Not signed in to Plex"))
        return authenticationManager.checkSession(credentials, block(address, credentials))
    }
}
