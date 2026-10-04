package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import kotlinx.coroutines.CompletableDeferred

/**
 * A server that accepts every login unless [failure] is set. Set [pending] to hold an authentication until the test
 * completes it. Like the real ones, an authentication saves its address before contacting the server, and forgetting
 * the login keeps the address.
 */
class FakeServerAuthentication(
    var saved: SavedServerLogin = SavedServerLogin(),
) : ServerAuthentication {
    var failure: Throwable? = null
    var pending: CompletableDeferred<Unit>? = null
    val authenticated = mutableListOf<ServerLogin>()
    var remembered: ServerLogin? = null
    var forgotten = 0
    var forgottenServer = 0

    override fun savedLogin(): SavedServerLogin = saved

    override suspend fun authenticate(login: ServerLogin): Result<Unit> {
        authenticated += login
        saved = saved.copy(address = login.address)
        pending?.await()
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override fun rememberLogin(login: ServerLogin) {
        remembered = login
        saved = saved.copy(username = login.username, password = login.password)
    }

    override fun forgetLogin() {
        saved = saved.copy(username = null, password = null)
        forgotten++
    }

    override fun forgetServer() {
        saved = SavedServerLogin()
        forgottenServer++
    }
}
