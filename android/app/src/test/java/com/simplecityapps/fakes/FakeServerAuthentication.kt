package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.sources.servers.SavedServerLogin
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerLogin
import kotlinx.coroutines.CompletableDeferred

/**
 * A server that accepts every login unless [failure] is set. Set [pending] to hold an authentication until the test
 * completes it.
 */
class FakeServerAuthentication(
    var saved: SavedServerLogin = SavedServerLogin(),
) : ServerAuthentication {
    var failure: Throwable? = null
    var pending: CompletableDeferred<Unit>? = null
    val authenticated = mutableListOf<ServerLogin>()
    var remembered: ServerLogin? = null
    var forgotten = 0

    override fun savedLogin(): SavedServerLogin = saved

    override suspend fun authenticate(login: ServerLogin): Result<Unit> {
        authenticated += login
        pending?.await()
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override fun rememberLogin(login: ServerLogin) {
        remembered = login
    }

    override fun forgetLogin() {
        forgotten++
    }
}
