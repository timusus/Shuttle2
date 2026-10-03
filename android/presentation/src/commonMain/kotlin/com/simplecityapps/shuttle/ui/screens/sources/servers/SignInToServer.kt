package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInMethod
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/**
 * Signs in to a [type] server with [login][invoke]. Once it's in, the sign-in is recorded for analytics, and the login
 * is saved for next time if the user asked to remember it; a failure is recorded too, by its [SignInFailureClassifier]
 * bucket. Signing in doesn't start the server trial: the first stream
 * or download does (ServerAccessGate), so a sign-in the user backs out of doesn't use it up.
 */
class SignInToServer @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
    private val analytics: MonetisationAnalytics,
    private val classifyFailure: SignInFailureClassifier,
) {
    sealed interface Result {
        data object Success : Result

        /** [message] says what went wrong, for the user. */
        data class Failure(val message: String) : Result
    }

    suspend operator fun invoke(
        type: MediaProviderType,
        login: ServerLogin,
        rememberLogin: Boolean,
    ): Result {
        val authentication = authentications.getValue(type)
        return authentication.authenticate(login).fold(
            onSuccess = {
                analytics.serverConnected(type, SignInMethod.Password)
                if (rememberLogin) authentication.rememberLogin(login)
                Result.Success
            },
            onFailure = { error ->
                logger.error { "$type authentication failed. Error ${error.message}" }
                analytics.signInFailed(type, SignInMethod.Password, classifyFailure.classify(error))
                Result.Failure(error.message ?: UNKNOWN_ERROR_MESSAGE)
            },
        )
    }

    private companion object {
        const val UNKNOWN_ERROR_MESSAGE = "An unknown error occurred."
        val logger = Logger.tagged("SignInToServer")
    }
}
