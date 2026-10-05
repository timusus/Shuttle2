package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put

/** Plex's playlist edits (#916): a playlist's entries, adding tracks, removing an entry and moving one. */
class PlaylistService(private val client: HttpClient) {
    /** Every entry of the playlist, in order, each with its playlistItemID. */
    suspend fun entries(
        url: String,
        token: String,
        playlistId: String
    ): NetworkResult<QueryResult> = client.networkResult {
        get("$url/playlists/$playlistId/items") {
            header(PLEX_TOKEN, token)
        }
    }

    /** Adds the items [uri] names (`server://{machineId}/com.plexapp.plugins.library/library/metadata/{ratingKeys}`) to the end of the playlist, in order. */
    suspend fun add(
        url: String,
        token: String,
        playlistId: String,
        uri: String
    ): NetworkResult<Unit> = client.networkResult {
        put("$url/playlists/$playlistId/items") {
            header(PLEX_TOKEN, token)
            parameter("uri", uri)
        }
    }

    suspend fun remove(
        url: String,
        token: String,
        playlistId: String,
        entryId: String
    ): NetworkResult<Unit> = client.networkResult {
        delete("$url/playlists/$playlistId/items/$entryId") {
            header(PLEX_TOKEN, token)
        }
    }

    /** Moves the entry to just after the entry [after], or to the top without one. */
    suspend fun move(
        url: String,
        token: String,
        playlistId: String,
        entryId: String,
        after: String?
    ): NetworkResult<Unit> = client.networkResult {
        put("$url/playlists/$playlistId/items/$entryId/move") {
            header(PLEX_TOKEN, token)
            parameter("after", after)
        }
    }

    suspend fun rename(
        url: String,
        token: String,
        playlistId: String,
        title: String
    ): NetworkResult<Unit> = client.networkResult {
        put("$url/playlists/$playlistId") {
            header(PLEX_TOKEN, token)
            parameter("title", title)
        }
    }

    suspend fun delete(
        url: String,
        token: String,
        playlistId: String
    ): NetworkResult<Unit> = client.networkResult {
        delete("$url/playlists/$playlistId") {
            header(PLEX_TOKEN, token)
        }
    }
}
