package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.*

interface ItemsService {

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemsImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Query("recursive") recursive: Boolean = true,
        @Query("includeItemTypes") itemTypes: String = "Audio",
        @Query("fields") fields: String = "Genres"
    ): NetworkResult<QueryResult>
}

suspend fun ItemsService.items(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String = "Genres",
): NetworkResult<QueryResult> {
    return itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields)
}