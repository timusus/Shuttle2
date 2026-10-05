package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.mediaprovider.server.QuickConnectCode
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import kotlinx.coroutines.CompletableDeferred

/**
 * A Quick Connect flow that reports [pollState] to every poll unless [pending] is set, which holds the poll until
 * the test resolves it — letting a test change [pollState] between two polls. [initiatePending], if set, holds
 * [initiate] the same way, to let a test drive two calls that overlap before either resolves.
 */
class FakeQuickConnectAuthentication : QuickConnectAuthentication {
    var enabled = true
    var enabledCheckThrows: Throwable? = null
    var initiateFailure: Throwable? = null
    var pollFailure: Throwable? = null
    var authenticateFailure: Throwable? = null
    var pollState = QuickConnectPollState.Pending
    var pending: CompletableDeferred<Unit>? = null
    var initiatePending: CompletableDeferred<Unit>? = null
    var initiateCallCount = 0
    var pollCount = 0
    var enabledChecks = 0
    val authenticated = mutableListOf<Pair<String, String>>()

    override suspend fun isEnabled(address: String): Boolean {
        enabledChecks++
        return enabledCheckThrows?.let { throw it } ?: enabled
    }

    override suspend fun initiate(address: String): Result<QuickConnectCode> {
        initiateCallCount++
        initiatePending?.await()
        return initiateFailure?.let { Result.failure(it) } ?: Result.success(QuickConnectCode("123456", "secret-1"))
    }

    override suspend fun poll(address: String, secret: String): Result<QuickConnectPollState> {
        pollCount++
        pending?.await()
        return pollFailure?.let { Result.failure(it) } ?: Result.success(pollState)
    }

    override suspend fun authenticate(address: String, secret: String): Result<Unit> {
        authenticated += address to secret
        return authenticateFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
