package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.PinAuthentication
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInMethod
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/**
 * Signs in to [server], one of the servers [SignInWithPin] listed, and records the sign-in (or why it failed) for
 * analytics. Its success says whether it switched servers: false when [server] was already the one signed in.
 */
class ConnectToAccountServer @Inject constructor(
    private val authentications: Map<MediaProviderType, PinAuthentication>,
    private val analytics: MonetisationAnalytics,
    private val classifyFailure: SignInFailureClassifier,
) {
    sealed interface Result {
        data class Success(val switchedServer: Boolean) : Result

        /** [message] says what went wrong, for the user. */
        data class Failure(val message: String) : Result
    }

    suspend operator fun invoke(type: MediaProviderType, server: AccountServer): Result {
        val authentication = authentications.getValue(type)
        val sameServer = authentication.isSignedInTo(server)
        return authentication.connect(server).fold(
            onSuccess = {
                analytics.serverConnected(type, SignInMethod.Pin)
                Result.Success(switchedServer = !sameServer)
            },
            onFailure = { error ->
                analytics.signInFailed(type, SignInMethod.Pin, classifyFailure.classify(error))
                Result.Failure(error.message.orEmpty())
            },
        )
    }
}
