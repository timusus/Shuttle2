package com.simplecityapps.provider.plex

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.LoginCredentials
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.provider.plex.http.authenticate
import com.simplecityapps.shuttle.model.Song
import timber.log.Timber

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

    suspend fun authenticate(
        address: String,
        loginCredentials: LoginCredentials
    ): Result<AuthenticatedCredentials> {
        Timber.d("authenticate(address: $address)")
        val authenticationResult =
            userService.authenticate(
                username = loginCredentials.username,
                password = loginCredentials.password,
                authCode = loginCredentials.authCode
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

    fun buildPlexPath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid plex address (${credentialStore.address})")
            return null
        }

        return "${credentialStore.address}${song.externalId}" +
            "?X-Plex-Token=${authenticatedCredentials.accessToken}" +
            "&X-Plex-Client-Identifier=s2-music-payer" +
            "&X-Plex-Device=Android"
    }
}
