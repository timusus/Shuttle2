package com.simplecityapps.provider.plex

import android.net.Uri
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.plex.http.*
import timber.log.Timber
import java.net.URLEncoder

class PlexAuthenticationManager(
    private val userService: UserService,
    private val credentialStore: CredentialStore
) {

    fun getLoginCredentials(): LoginCredentials? {
        return credentialStore.loginCredentials
    }

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? {
        return credentialStore.authenticatedCredentials
    }

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    fun getAddress(): String? {
        return credentialStore.address
    }

    suspend fun authenticate(address: String, loginCredentials: LoginCredentials): Result<AuthenticatedCredentials> {
        Timber.d("authenticate(address: $address)")
        val authenticationResult = userService.authenticate(
            username = loginCredentials.username,
            password = loginCredentials.password
        )

        return when (authenticationResult) {
            is NetworkResult.Success<AuthenticationResult> -> {
                val authenticatedCredentials = AuthenticatedCredentials(authenticationResult.body.user.authToken, authenticationResult.body.user.id)
                credentialStore.authenticatedCredentials = authenticatedCredentials
                Result.success(authenticatedCredentials)
            }
            is NetworkResult.Failure -> {
                (authenticationResult.error as? RemoteServiceHttpError)?.let { error ->
                    if (error.httpStatusCode == HttpStatusCode.Unauthorized) {
                        credentialStore.authenticatedCredentials = null
                    }
                }
                Result.failure(authenticationResult.error)
            }
        }
    }

    fun buildPlexPath(path: Uri, authenticatedCredentials: AuthenticatedCredentials): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid plex address (${credentialStore.address})")
            return null
        }

        return "${credentialStore.address}" +
                "/music/:/transcode/universal/start.m3u8" +
                "?path=${URLEncoder.encode(path.path, Charsets.UTF_8.name())}" +
                "&directStreamAudio=1" +
                "&protocol=hls" +
                "&directPlay=1" +
                "&hasMDE=1" +
                "&download=1" +
                "&X-Plex-Token=${authenticatedCredentials.accessToken}" +
                "&X-Plex-Client-Identifier=s2-music-payer" +
                "&X-Plex-Device=Android" +
                "&X-Plex-Session-Identifier=${path}" +
                "&X-Plex-Client-Profile-Extra=${URLEncoder.encode("add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac,mp3)", Charsets.UTF_8.name())}"
    }
}