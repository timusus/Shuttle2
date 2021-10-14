package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service

import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.ItemResponse
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.QueryResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

@OptIn(InternalAPI::class)
class ItemsService(private val httpClient: HttpClient) {

    private suspend fun items(
        url: String,
        token: String,
        recursive: Boolean = true,
        itemTypes: String,
        fields: String?,
        limit: Int = 2500,
        startIndex: Int = 0,
        userId: String? = null
    ): Result<QueryResponse> {
        return runCatching {
            httpClient.get(url) {
                method = HttpMethod.Get
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append("X-Emby-Token", token)
                }
                parameter("recursive", recursive)
                parameter("includeItemTypes", itemTypes)
                parameter("fields", fields)
                parameter("limit", limit)
                parameter("startIndex", startIndex)
                parameter("userId", userId)
            }
        }
    }

    private suspend fun item(
        url: String,
        token: String
    ): Result<ItemResponse> {
        return runCatching {
            httpClient.get(url) {
                method = HttpMethod.Get
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append("X-Emby-Token", token)
                }
            }
        }
    }

    suspend fun audioItems(
        url: String,
        token: String,
        userId: String,
        recursive: Boolean = true,
        itemTypes: String = "Audio",
        fields: String? = "Genres",
        limit: Int = 2500,
        startIndex: Int = 0
    ): Result<QueryResponse> {
        return items("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)
    }

    suspend fun playlists(
        url: String,
        token: String,
        userId: String,
        recursive: Boolean = true,
        itemTypes: String = "Playlist",
        fields: String? = null,
        limit: Int = 2500,
        startIndex: Int = 0
    ): Result<QueryResponse> {
        return items("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)
    }

    suspend fun playlistItems(
        url: String,
        token: String,
        playlistId: String,
        recursive: Boolean = true,
        itemTypes: String = "Audio",
        fields: String? = null,
        limit: Int = 2500,
        startIndex: Int = 0,
        userId: String
    ): Result<QueryResponse> {
        return items("$url/Playlists/$playlistId/Items", token, recursive, itemTypes, fields, limit, startIndex, userId)
    }

    suspend fun item(
        url: String,
        token: String,
        userId: String,
        itemId: String
    ): Result<ItemResponse> {
        return item("$url/Users/$userId/Items/$itemId", token)
    }
}

