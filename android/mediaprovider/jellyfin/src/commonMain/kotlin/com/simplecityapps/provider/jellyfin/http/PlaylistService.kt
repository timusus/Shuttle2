package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.mediaprovider.server.mediabrowser.QueryResult
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Jellyfin's playlist edits (#916): a playlist's entries, adding songs, removing entries, moving one, renaming and deleting it. */
class PlaylistService(private val client: HttpClient) {
    /** Every entry of the playlist, in order, each with its PlaylistItemId: whatever its type, so an entry's index is its place in the playlist. */
    suspend fun entries(
        url: String,
        authorization: String,
        playlistId: String,
        userId: String
    ): NetworkResult<QueryResult> = client.networkResult {
        get("$url/Playlists/$playlistId/Items") {
            header(HttpHeaders.Authorization, authorization)
            parameter("userId", userId)
        }
    }

    /** Adds the items to the end of the playlist, in order. */
    suspend fun add(
        url: String,
        authorization: String,
        playlistId: String,
        userId: String,
        itemIds: List<String>
    ): NetworkResult<Unit> = client.networkResult {
        post("$url/Playlists/$playlistId/Items") {
            header(HttpHeaders.Authorization, authorization)
            parameter("ids", itemIds.joinToString(","))
            parameter("userId", userId)
        }
    }

    suspend fun remove(
        url: String,
        authorization: String,
        playlistId: String,
        entryIds: List<String>
    ): NetworkResult<Unit> = client.networkResult {
        delete("$url/Playlists/$playlistId/Items") {
            header(HttpHeaders.Authorization, authorization)
            parameter("entryIds", entryIds.joinToString(","))
        }
    }

    /** Moves the entry so it ends up at [newIndex]. */
    suspend fun move(
        url: String,
        authorization: String,
        playlistId: String,
        entryId: String,
        newIndex: Int
    ): NetworkResult<Unit> = client.networkResult {
        post("$url/Playlists/$playlistId/Items/$entryId/Move/$newIndex") {
            header(HttpHeaders.Authorization, authorization)
        }
    }

    /** Sets the playlist's name: `POST /Playlists/{id}` takes just the fields that change (Jellyfin 10.9 and later). */
    suspend fun rename(
        url: String,
        authorization: String,
        playlistId: String,
        name: String
    ): NetworkResult<Unit> = client.networkResult {
        post("$url/Playlists/$playlistId") {
            header(HttpHeaders.Authorization, authorization)
            contentType(ContentType.Application.Json)
            setBody(mapOf("Name" to name))
        }
    }

    suspend fun delete(
        url: String,
        authorization: String,
        playlistId: String
    ): NetworkResult<Unit> = client.networkResult {
        delete("$url/Items/$playlistId") {
            header(HttpHeaders.Authorization, authorization)
        }
    }
}
