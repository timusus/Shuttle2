package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable

/** [ListenBrainz API](https://listenbrainz.readthedocs.io/en/latest/users/api/core.html) root. */
const val LISTENBRAINZ_BASE_URL = "https://api.listenbrainz.org/1/"

@Serializable
data class ListenBrainzValidateResponse(
    val valid: Boolean = false,
    val user_name: String? = null
)

@Serializable
data class ListenBrainzStatusResponse(
    val status: String? = null
)

/** The raw ListenBrainz calls; [ListenBrainzClient] builds and reads them. */
class ListenBrainzApi(private val client: HttpClient) {
    suspend fun validateToken(token: String): NetworkResult<ListenBrainzValidateResponse> = client.networkResult {
        get("${LISTENBRAINZ_BASE_URL}validate-token") { header(HttpHeaders.Authorization, "Token $token") }
    }

    /** [json] is sent as is: the payload leaves out absent fields, which the client's JSON config would write as nulls. */
    suspend fun submitListens(
        token: String,
        json: String
    ): NetworkResult<ListenBrainzStatusResponse> = client.networkResult {
        post("${LISTENBRAINZ_BASE_URL}submit-listens") {
            header(HttpHeaders.Authorization, "Token $token")
            setBody(TextContent(json, ContentType.Application.Json))
        }
    }
}
