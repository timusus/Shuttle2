package com.simplecityapps.shuttle.ui.screens.sources.servers.jellyfin

import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.shuttle.ui.screens.sources.servers.SavedServerLogin
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerLogin
import javax.inject.Inject

class JellyfinServerAuthentication @Inject constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
) : ServerAuthentication {
    override fun savedLogin(): SavedServerLogin {
        val credentials = authenticationManager.getLoginCredentials()
        return SavedServerLogin(authenticationManager.getAddress(), credentials?.username, credentials?.password)
    }

    override suspend fun authenticate(login: ServerLogin): Result<Unit> {
        authenticationManager.setAddress(login.address)
        return authenticationManager.authenticate(login.address, login.credentials()).map {}
    }

    override fun rememberLogin(login: ServerLogin) = authenticationManager.setLoginCredentials(login.credentials())

    override fun forgetLogin() = authenticationManager.setLoginCredentials(null)

    private fun ServerLogin.credentials() = LoginCredentials(username, password)
}
