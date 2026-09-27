package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.networking.userDescription
import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs a full Quick Connect attempt: initiates it, shows the code, polls until the user approves it in another
 * Jellyfin client (or it's denied, expires, or fails), then redeems it for a token. Cancelling the collecting
 * coroutine (the user backs out) stops the polling loop; nothing here outlives the collector.
 */
class SignInWithQuickConnect @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards QuickConnectAuthentication>,
    private val analytics: ServerSignInAnalytics,
) {
    sealed interface State {
        data class AwaitingApproval(val code: String) : State

        data object Success : State

        data object Expired : State

        /** [message] says what went wrong, for the user. */
        data class Failed(val message: String) : State
    }

    private sealed interface PollResult {
        data object Approved : PollResult

        data class Failed(val message: String) : PollResult
    }

    operator fun invoke(type: MediaProviderType, address: String): Flow<State> = flow {
        val authentication = authentications.getValue(type)

        val code = authentication.initiate(address).getOrElse { error ->
            emit(State.Failed(error.userDescription()))
            return@flow
        }
        emit(State.AwaitingApproval(code.code))

        when (val result = withTimeoutOrNull(EXPIRY_MILLIS) { pollUntilResolved(authentication, address, code.secret) }) {
            null -> emit(State.Expired)

            is PollResult.Failed -> emit(State.Failed(result.message))

            PollResult.Approved -> {
                authentication.authenticate(address, code.secret).fold(
                    onSuccess = {
                        analytics.onServerConnected(type)
                        emit(State.Success)
                    },
                    onFailure = { error -> emit(State.Failed(error.userDescription())) },
                )
            }
        }
    }

    private suspend fun pollUntilResolved(authentication: QuickConnectAuthentication, address: String, secret: String): PollResult {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            when (val poll = authentication.poll(address, secret).getOrElse { error -> return PollResult.Failed(error.userDescription()) }) {
                QuickConnectPollState.Authenticated -> return PollResult.Approved
                QuickConnectPollState.Denied -> return PollResult.Failed(DENIED_MESSAGE)
                QuickConnectPollState.Pending -> Unit
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 5_000L
        private const val EXPIRY_MILLIS = 10 * 60 * 1_000L
        private const val DENIED_MESSAGE = "Quick Connect sign-in was denied."
    }
}
