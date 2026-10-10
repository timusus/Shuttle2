package com.simplecityapps.provider.emby.http

import com.simplecityapps.mediaprovider.server.mediabrowser.EMBY_AUTHORIZATION
import com.simplecityapps.mediaprovider.server.mediabrowser.EMBY_TOKEN
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post

/** Emby's favourite endpoints, which answer with the item's user data; only the status matters, so the body is discarded. */
class FavouriteService(private val client: HttpClient) {
    suspend fun favourite(
        url: String,
        token: String,
        authorization: String
    ): NetworkResult<Unit> = client.networkResult {
        post(url) {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
        }
    }

    suspend fun unfavourite(
        url: String,
        token: String,
        authorization: String
    ): NetworkResult<Unit> = client.networkResult {
        delete(url) {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
        }
    }
}
