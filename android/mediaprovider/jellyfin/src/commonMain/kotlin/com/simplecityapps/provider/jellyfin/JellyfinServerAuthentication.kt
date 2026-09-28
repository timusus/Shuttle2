package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.networking.userDescription
import dev.zacsweers.metro.Inject

/** Jellyfin's password sign-in, for the shared sign-in form. */
class JellyfinServerAuthentication @Inject constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
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

    override fun forgetServer() = authenticationManager.forgetServer()

    private fun ServerLogin.credentials() = LoginCredentials(username, password)
}
