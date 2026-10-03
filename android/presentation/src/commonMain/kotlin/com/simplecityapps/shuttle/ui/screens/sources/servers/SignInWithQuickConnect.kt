package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.analytics.SignInMethod
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs a full Quick Connect attempt: initiates it, shows the code, polls until the user approves it in another
 * Jellyfin client (or it's denied, expires, or fails), then redeems it for a token. Cancelling the collecting
 * coroutine (the user backs out) stops the polling loop; nothing here outlives the collector. The sign-in, or why it
 * failed, is recorded for analytics; backing out records nothing.
 */
class SignInWithQuickConnect @Inject constructor(
    private val authentications: Map<MediaProviderType, QuickConnectAuthentication>,
    private val analytics: MonetisationAnalytics,
    private val classifyFailure: SignInFailureClassifier,
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

        data class Failed(val message: String, val reason: SignInFailureReason) : PollResult
    }

    operator fun invoke(type: MediaProviderType, address: String): Flow<State> = flow {
        val authentication = authentications.getValue(type)

        val code = authentication.initiate(address).getOrElse { error ->
            failed(type, classifyFailure.classify(error))
            emit(State.Failed(error.message.orEmpty()))
            return@flow
        }
        emit(State.AwaitingApproval(code.code))

        when (val result = withTimeoutOrNull(EXPIRY_MILLIS) { pollUntilResolved(authentication, address, code.secret) }) {
            null -> {
                failed(type, SignInFailureReason.Expired)
                emit(State.Expired)
            }

            is PollResult.Failed -> {
                failed(type, result.reason)
                emit(State.Failed(result.message))
            }

            PollResult.Approved -> {
                authentication.authenticate(address, code.secret).fold(
                    onSuccess = {
                        analytics.serverConnected(type, SignInMethod.QuickConnect)
                        emit(State.Success)
                    },
                    onFailure = { error ->
                        failed(type, classifyFailure.classify(error))
                        emit(State.Failed(error.message.orEmpty()))
                    },
                )
            }
        }
    }

    private suspend fun pollUntilResolved(authentication: QuickConnectAuthentication, address: String, secret: String): PollResult {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            when (
                val poll = authentication.poll(address, secret).getOrElse { error ->
                    return PollResult.Failed(error.message.orEmpty(), classifyFailure.classify(error))
                }
            ) {
                QuickConnectPollState.Authenticated -> return PollResult.Approved
                QuickConnectPollState.Denied -> return PollResult.Failed(DENIED_MESSAGE, SignInFailureReason.Denied)
                QuickConnectPollState.Pending -> Unit
            }
        }
    }

    private fun failed(type: MediaProviderType, reason: SignInFailureReason) = analytics.signInFailed(type, SignInMethod.QuickConnect, reason)

    companion object {
        private const val POLL_INTERVAL_MILLIS = 5_000L
        private const val EXPIRY_MILLIS = 10 * 60 * 1_000L
        private const val DENIED_MESSAGE = "Quick Connect sign-in was denied."
    }
}
