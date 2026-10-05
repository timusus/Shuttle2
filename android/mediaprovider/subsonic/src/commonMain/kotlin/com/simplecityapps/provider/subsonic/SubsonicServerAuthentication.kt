package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.subsonic.http.SubsonicError
import dev.zacsweers.metro.Inject

/**
 * Subsonic's sign-in, for the shared sign-in form: a username and password, or an API key in the password field with
 * the username left empty, on a server with OpenSubsonic's `apiKeyAuthentication`.
 */
class SubsonicServerAuthentication @Inject constructor(
    private val authenticationManager: SubsonicAuthenticationManager,
) : ServerAuthentication {
    override fun savedLogin(): SavedServerLogin {
        val credentials = authenticationManager.getLoginCredentials()
        return SavedServerLogin(authenticationManager.getAddress(), credentials?.username, credentials?.password)
    }

    override suspend fun authenticate(login: ServerLogin): Result<Unit> {
        authenticationManager.setAddress(login.address)
        return authenticationManager.authenticate(login.address, login.credentials())
            .map {}
            // The server's own message says what's wrong ("Wrong username or password"); there's no HTTP status to show
            .recoverCatching { error -> throw Exception((error as? SubsonicError)?.serverMessage ?: error.userDescription(), error) }
    }

    override fun rememberLogin(login: ServerLogin) = authenticationManager.setLoginCredentials(login.credentials())

    override fun forgetLogin() = authenticationManager.setLoginCredentials(null)

    override fun forgetServer() = authenticationManager.forgetServer()

    private fun ServerLogin.credentials() = LoginCredentials(username, password)
}
