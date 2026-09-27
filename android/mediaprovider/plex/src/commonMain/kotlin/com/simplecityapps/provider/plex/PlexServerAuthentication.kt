package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.networking.userDescription
import dev.zacsweers.metro.Inject

/** Plex's password sign-in, for the shared sign-in form. */
class PlexServerAuthentication @Inject constructor(
    private val authenticationManager: PlexAuthenticationManager,
) : ServerAuthentication {
    override fun savedLogin(): SavedServerLogin {
        val credentials = authenticationManager.getLoginCredentials()
        return SavedServerLogin(authenticationManager.getAddress(), credentials?.username, credentials?.password)
    }

    override suspend fun authenticate(login: ServerLogin): Result<Unit> {
        authenticationManager.setAddress(login.address)
        return authenticationManager.authenticate(login.address, login.credentials())
            .map {}
            .recoverCatching { error -> throw Exception(error.userDescription(), error) }
    }

    override fun rememberLogin(login: ServerLogin) = authenticationManager.setLoginCredentials(login.credentials())

    override fun forgetLogin() = authenticationManager.setLoginCredentials(null)

    private fun ServerLogin.credentials() = LoginCredentials(username, password, authCode)
}
