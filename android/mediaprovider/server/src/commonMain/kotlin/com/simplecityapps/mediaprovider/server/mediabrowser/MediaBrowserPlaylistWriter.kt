package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.ServerPlaylistWriter
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.toPlaylistWriteResult
import com.simplecityapps.mediaprovider.then
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the edits made in S2 to a playlist on a [MediaBrowserServer] back to it (#916): `POST /Playlists/{id}/Items?ids=`
 * adds, `DELETE /Playlists/{id}/Items?entryIds=` removes, `POST /Playlists/{id}/Items/{entryId}/Move/{newIndex}` moves,
 * and `DELETE /Items/{id}` deletes. Renaming differs: Jellyfin (10.9 and later) takes just the new name at
 * `POST /Playlists/{id}`; Emby's item update takes the whole item, so the playlist's item is read and sent back to
 * `POST /Items/{id}` with the new name. These routes sit at the address itself, without Emby's `/emby` prefix.
 */
class MediaBrowserPlaylistWriter(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val client: HttpClient,
    private val clientIdentity: ClientIdentity
) : ServerPlaylistWriter {
    private val server = authenticationManager.server

    override val type = server.type

    override suspend fun entries(playlistId: String): PlaylistWriteResult<List<ServerPlaylistEntry>> = request { address, credentials ->
        // Every entry of the playlist, in order, whatever its type, so an entry's index is its place in the playlist
        client.networkResult<QueryResult> {
            get("$address/Playlists/$playlistId/Items") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
                parameter("userId", credentials.userId)
            }
        }
    }.toPlaylistWriteResult { result ->
        result.items.mapNotNull { item -> item.playlistItemId?.let { entryId -> ServerPlaylistEntry(entryId, server.songPath(item)) } }
    }

    override suspend fun add(
        playlistId: String,
        songPaths: List<String>
    ): PlaylistWriteResult<Unit> = request { address, credentials ->
        client.networkResult {
            post("$address/Playlists/$playlistId/Items") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
                parameter("ids", songPaths.joinToString(",") { path -> path.removePrefix(server.songPathPrefix) })
                parameter("userId", credentials.userId)
            }
        }
    }.toPlaylistWriteResult()

    override suspend fun remove(
        playlistId: String,
        entryIds: List<String>
    ): PlaylistWriteResult<Unit> = request { address, credentials ->
        client.networkResult {
            delete("$address/Playlists/$playlistId/Items") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
                parameter("entryIds", entryIds.joinToString(","))
            }
        }
    }.toPlaylistWriteResult()

    /** Moves the entry so it ends up at [index]. */
    override suspend fun move(
        playlistId: String,
        entryId: String,
        index: Int,
        after: String?
    ): PlaylistWriteResult<Unit> = request { address, credentials ->
        client.networkResult {
            post("$address/Playlists/$playlistId/Items/$entryId/Move/$index") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
            }
        }
    }.toPlaylistWriteResult()

    override suspend fun rename(
        playlistId: String,
        name: String
    ): PlaylistWriteResult<Unit> = when (server) {
        MediaBrowserServer.Jellyfin -> request { address, credentials ->
            client.networkResult {
                post("$address/Playlists/$playlistId") {
                    server.authorizeSession(this, credentials.accessToken, clientIdentity)
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("Name" to name))
                }
            }
        }.toPlaylistWriteResult()

        MediaBrowserServer.Emby -> request { address, credentials ->
            client.networkResult<JsonObject> {
                get("$address/Users/${credentials.userId}/Items/$playlistId") {
                    server.authorizeSession(this, credentials.accessToken, clientIdentity)
                }
            }
        }.toPlaylistWriteResult { item -> item }.then { item ->
            request { address, credentials ->
                client.networkResult {
                    post("$address/Items/$playlistId") {
                        server.authorizeSession(this, credentials.accessToken, clientIdentity)
                        contentType(ContentType.Application.Json)
                        setBody(JsonObject(item + ("Name" to JsonPrimitive(name))))
                    }
                }
            }.toPlaylistWriteResult()
        }
    }

    override suspend fun delete(playlistId: String): PlaylistWriteResult<Unit> = request { address, credentials ->
        client.networkResult {
            delete("$address/Items/$playlistId") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
            }
        }
    }.toPlaylistWriteResult()

    /** [block]'s request with the signed-in session, signing out if the server rejects it; a failure when signed out. */
    private suspend fun <T : Any> request(block: suspend (address: String, credentials: AuthenticatedCredentials) -> NetworkResult<T>): NetworkResult<T> {
        val address = authenticationManager.getAddress() ?: return NetworkResult.Failure(IllegalStateException("No ${server.name} address"))
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return NetworkResult.Failure(IllegalStateException("Not signed in to ${server.name}"))
        return authenticationManager.checkSession(credentials, block(address, credentials))
    }
}
