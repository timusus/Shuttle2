package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** plex.tv's account API: sign-in PINs and the account's servers, plus whether a server answers at an address. */
class UserService(private val client: HttpClient) {
    /** A short PIN, which works at plex.tv/link as well as in Plex's web sign-in. */
    suspend fun createPin(): NetworkResult<Pin> = client.networkResult {
        post(PINS_URL) {
            parameter("strong", "false")
        }
    }

    suspend fun pin(
        id: Long,
        code: String
    ): NetworkResult<Pin> = client.networkResult {
        get("$PINS_URL/$id") {
            parameter("code", code)
        }
    }

    /** Everything the account signed in with [accountToken] can reach: servers, players and the like, owned or shared. */
    suspend fun resources(accountToken: String): NetworkResult<List<Resource>> = client.networkResult {
        get(RESOURCES_URL) {
            parameter("includeHttps", "1")
            parameter("includeRelay", "1")
            header(PLEX_TOKEN, accountToken)
        }
    }

    /** Whether a Plex server answers its `/identity` at [uri] within [timeoutMillis]. Never throws. */
    suspend fun isReachable(
        uri: String,
        timeoutMillis: Long = PROBE_TIMEOUT_MILLIS
    ): Boolean = withTimeoutOrNull(timeoutMillis) {
        try {
            client.get("${uri.trimEnd('/')}/identity").status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    } ?: false

    companion object {
        const val PINS_URL = "https://plex.tv/api/v2/pins"
        const val RESOURCES_URL = "https://plex.tv/api/v2/resources"
        const val PROBE_TIMEOUT_MILLIS = 6_000L
    }
}
