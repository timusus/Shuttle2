package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import dev.zacsweers.metro.Inject

/**
 * Plex's part of the shared sign-in form: the saved server, and forgetting it. Plex signs in with a plex.tv PIN
 * ([PlexPinAuthentication]) rather than a password, so [authenticate] always fails and there's no login to remember.
 */
class PlexServerAuthentication @Inject constructor(
    private val authenticationManager: PlexAuthenticationManager,
) : ServerAuthentication {
    override fun savedLogin(): SavedServerLogin = SavedServerLogin(address = authenticationManager.getAddress())

    override suspend fun authenticate(login: ServerLogin): Result<Unit> = Result.failure(UnsupportedOperationException("Plex signs in with plex.tv."))

    override fun rememberLogin(login: ServerLogin) = Unit

    /** Forgets any password the old plex.tv sign-in saved. */
    override fun forgetLogin() {
        authenticationManager.credentialStore.loginCredentials = null
    }

    override fun forgetServer() = authenticationManager.forgetServer()
}
