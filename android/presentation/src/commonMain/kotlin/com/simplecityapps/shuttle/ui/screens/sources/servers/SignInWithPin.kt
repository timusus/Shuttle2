package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.PinAuthentication
import com.simplecityapps.mediaprovider.server.SignInPin
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
 * Signs in to [MediaProviderType]'s account with a PIN: creates it, shows it, polls until the user approves it on the
 * provider's website (or it runs out, or fails), then lists the account's servers, the user's own first, for
 * [ConnectToAccountServer]. Cancelling the collecting coroutine (the user backs out) stops the polling; nothing here
 * outlives the collector. A failure is recorded for analytics; backing out records nothing.
 */
class SignInWithPin @Inject constructor(
    private val authentications: Map<MediaProviderType, PinAuthentication>,
    private val analytics: MonetisationAnalytics,
    private val classifyFailure: SignInFailureClassifier,
) {
    sealed interface State {
        data class AwaitingApproval(val pin: SignInPin) : State

        /** The PIN was approved: [servers] are the account's, to choose from. Never empty. */
        data class Approved(val servers: List<AccountServer>) : State

        data object Expired : State

        /** [message] says what went wrong, for the user. */
        data class Failed(val message: String) : State
    }

    operator fun invoke(type: MediaProviderType): Flow<State> = flow {
        val authentication = authentications.getValue(type)

        val pin = authentication.createPin().getOrElse { error ->
            failed(type, classifyFailure.classify(error))
            emit(State.Failed(error.message.orEmpty()))
            return@flow
        }
        emit(State.AwaitingApproval(pin))

        val accountToken = withTimeoutOrNull(pin.expiresInSeconds * 1_000L) {
            pollUntilApproved(authentication, pin)
        }
        when {
            accountToken == null -> {
                failed(type, SignInFailureReason.Expired)
                emit(State.Expired)
            }

            accountToken.isFailure -> {
                val error = accountToken.exceptionOrNull()!!
                failed(type, classifyFailure.classify(error))
                emit(State.Failed(error.message.orEmpty()))
            }

            else -> authentication.servers(accountToken.getOrThrow()).fold(
                onSuccess = { servers ->
                    if (servers.isEmpty()) {
                        failed(type, SignInFailureReason.Other)
                        emit(State.Failed(NO_SERVERS_MESSAGE))
                    } else {
                        emit(State.Approved(servers.sortedWith(compareByDescending<AccountServer> { it.owned }.thenBy { it.name.lowercase() })))
                    }
                },
                onFailure = { error ->
                    failed(type, classifyFailure.classify(error))
                    emit(State.Failed(error.message.orEmpty()))
                },
            )
        }
    }

    /** The account's token once [pin] is approved, or the failure that stopped the polling. */
    private suspend fun pollUntilApproved(authentication: PinAuthentication, pin: SignInPin): Result<String> {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)
            val token = authentication.checkPin(pin).getOrElse { error -> return Result.failure(error) }
            if (token != null) return Result.success(token)
        }
    }

    private fun failed(type: MediaProviderType, reason: SignInFailureReason) = analytics.signInFailed(type, SignInMethod.Pin, reason)

    companion object {
        private const val POLL_INTERVAL_MILLIS = 2_000L
        private const val NO_SERVERS_MESSAGE = "There's no Plex Media Server on this account."
    }
}
