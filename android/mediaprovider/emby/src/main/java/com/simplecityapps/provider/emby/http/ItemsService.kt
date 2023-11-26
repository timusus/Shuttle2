package com.simplecityapps.provider.emby.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface ItemsService {
    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemsImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") itemTypes: String = "Audio",
        @Query("Fields") fields: String? = "Genres,ProductionYear",
        @Query("Limit") limit: Int = 2500,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("UserId") UserId: String? = null
    ): NetworkResult<QueryResult>

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String
    ): NetworkResult<Item>
}

suspend fun ItemsService.audioItems(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String? = "Genres,ProductionYear",
    limit: Int = 2500,
    startIndex: Int = 0
): NetworkResult<QueryResult> {
    return itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)
}

suspend fun ItemsService.playlists(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Playlist",
    fields: String? = null,
    limit: Int = 2500,
    startIndex: Int = 0
): NetworkResult<QueryResult> {
    return itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)
}

suspend fun ItemsService.playlistItems(
    url: String,
    token: String,
    playlistId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String? = null,
    limit: Int = 2500,
    startIndex: Int = 0,
    userId: String
): NetworkResult<QueryResult> {
    return itemsImpl("$url/Playlists/$playlistId/Items", token, recursive, itemTypes, fields, limit, startIndex, userId)
}

suspend fun ItemsService.item(
    url: String,
    token: String,
    userId: String,
    itemId: String
): NetworkResult<Item> {
    return itemImpl("$url/Users/$userId/Items/$itemId", token)
}
