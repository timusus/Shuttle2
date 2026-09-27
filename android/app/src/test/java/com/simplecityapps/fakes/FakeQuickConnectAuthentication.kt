package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectCode
import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectPollState
import kotlinx.coroutines.CompletableDeferred

/**
 * A Quick Connect flow that reports [pollState] to every poll unless [pending] is set, which holds the poll until
 * the test resolves it — letting a test change [pollState] between two polls.
 */
class FakeQuickConnectAuthentication : QuickConnectAuthentication {
    var enabled = true
    var enabledCheckThrows: Throwable? = null
    var initiateFailure: Throwable? = null
    var pollFailure: Throwable? = null
    var authenticateFailure: Throwable? = null
    var pollState = QuickConnectPollState.Pending
    var pending: CompletableDeferred<Unit>? = null
    val authenticated = mutableListOf<Pair<String, String>>()

    override suspend fun isEnabled(address: String): Boolean = enabledCheckThrows?.let { throw it } ?: enabled

    override suspend fun initiate(address: String): Result<QuickConnectCode> = initiateFailure?.let { Result.failure(it) } ?: Result.success(QuickConnectCode("123456", "secret-1"))

    override suspend fun poll(address: String, secret: String): Result<QuickConnectPollState> {
        pending?.await()
        return pollFailure?.let { Result.failure(it) } ?: Result.success(pollState)
    }

    override suspend fun authenticate(address: String, secret: String): Result<Unit> {
        authenticated += address to secret
        return authenticateFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
