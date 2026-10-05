package com.simplecityapps.provider.emby.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post

/** Emby's playlist edits (#916): a playlist's entries, adding songs, removing entries and moving one. */
class PlaylistService(private val client: HttpClient) {
    /** Every entry of the playlist, in order, each with its PlaylistItemId: whatever its type, so an entry's index is its place in the playlist. */
    suspend fun entries(
        url: String,
        token: String,
        authorization: String,
        playlistId: String,
        userId: String
    ): NetworkResult<QueryResult> = client.networkResult {
        get("$url/Playlists/$playlistId/Items") {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
            parameter("userId", userId)
        }
    }

    /** Adds the items to the end of the playlist, in order. */
    suspend fun add(
        url: String,
        token: String,
        authorization: String,
        playlistId: String,
        userId: String,
        itemIds: List<String>
    ): NetworkResult<Unit> = client.networkResult {
        post("$url/Playlists/$playlistId/Items") {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
            parameter("ids", itemIds.joinToString(","))
            parameter("userId", userId)
        }
    }

    suspend fun remove(
        url: String,
        token: String,
        authorization: String,
        playlistId: String,
        entryIds: List<String>
    ): NetworkResult<Unit> = client.networkResult {
        delete("$url/Playlists/$playlistId/Items") {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
            parameter("entryIds", entryIds.joinToString(","))
        }
    }

    /** Moves the entry so it ends up at [newIndex]. */
    suspend fun move(
        url: String,
        token: String,
        authorization: String,
        playlistId: String,
        entryId: String,
        newIndex: Int
    ): NetworkResult<Unit> = client.networkResult {
        post("$url/Playlists/$playlistId/Items/$entryId/Move/$newIndex") {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
        }
    }
}
