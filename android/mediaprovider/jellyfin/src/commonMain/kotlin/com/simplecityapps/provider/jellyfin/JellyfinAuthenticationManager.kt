package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.QuickConnectCode
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserAuthenticationManager
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServer
import com.simplecityapps.mediaprovider.server.mediabrowser.UserService
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.jellyfin.http.QuickConnectService
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode

/** Jellyfin's sign-in: the MediaBrowser one, plus Quick Connect. */
class JellyfinAuthenticationManager(
    httpClient: HttpClient,
    credentialStore: ServerCredentialStore,
    clientIdentity: ClientIdentity,
    streamProfile: StreamProfile
) : MediaBrowserAuthenticationManager(
    MediaBrowserServer.Jellyfin,
    UserService(httpClient, MediaBrowserServer.Jellyfin, clientIdentity),
    credentialStore,
    clientIdentity,
    streamProfile
) {
    private val quickConnectService = QuickConnectService(httpClient)

    /** Whether the server supports the Quick Connect sign-in flow. Servers before it existed return false, never an error. */
    suspend fun isQuickConnectEnabled(address: String): Boolean = when (val result = quickConnectService.isQuickConnectEnabled(address)) {
        is NetworkResult.Success -> result.body
        is NetworkResult.Failure -> false
    }

    /** Starts a Quick Connect attempt, returning the code to show the user and the secret used to poll and redeem it. */
    suspend fun initiateQuickConnect(address: String): Result<QuickConnectCode> = when (
        val result = quickConnectService.initiateQuickConnect(address, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success -> Result.success(QuickConnectCode(code = result.body.code, secret = result.body.secret))
        is NetworkResult.Failure -> Result.failure(result.error)
    }

    /** A 401 means the code was denied or expired server-side; Jellyfin's API has no separate signal for that. */
    suspend fun pollQuickConnect(address: String, secret: String): Result<QuickConnectPollState> = when (
        val result = quickConnectService.pollQuickConnect(address, secret, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success -> Result.success(if (result.body.authenticated) QuickConnectPollState.Authenticated else QuickConnectPollState.Pending)

        is NetworkResult.Failure ->
            if ((result.error as? RemoteServiceHttpError)?.httpStatusCode == HttpStatusCode.Unauthorized) {
                Result.success(QuickConnectPollState.Denied)
            } else {
                Result.failure(result.error)
            }
    }

    /** Redeems an approved Quick Connect secret for a token, stored the same way a password sign-in's token is. */
    suspend fun authenticateWithQuickConnect(address: String, secret: String): Result<AuthenticatedCredentials> = when (
        val result = quickConnectService.authenticateWithQuickConnect(address, secret, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success -> Result.success(store(result.body))
        is NetworkResult.Failure -> Result.failure(result.error)
    }
}
