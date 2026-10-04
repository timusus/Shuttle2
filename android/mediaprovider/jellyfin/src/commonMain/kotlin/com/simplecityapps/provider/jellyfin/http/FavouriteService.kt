package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders

/** Jellyfin's favourite endpoints, which answer with the item's user data; only the status matters, so the body is discarded. */
class FavouriteService(private val client: HttpClient) {
    suspend fun favourite(
        url: String,
        authorization: String
    ): NetworkResult<Unit> = client.networkResult {
        post(url) { header(HttpHeaders.Authorization, authorization) }
    }

    suspend fun unfavourite(
        url: String,
        authorization: String
    ): NetworkResult<Unit> = client.networkResult {
        delete(url) { header(HttpHeaders.Authorization, authorization) }
    }
}
