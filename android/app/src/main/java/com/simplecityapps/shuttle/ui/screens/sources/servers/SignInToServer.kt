package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.networking.userDescription
import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import timber.log.Timber

/**
 * Signs in to a [type] server with [login][invoke]. Once it's in, the sign-in is recorded for analytics, and the login
 * is saved for next time if the user asked to remember it. Signing in doesn't start the server trial: the first stream
 * or download does (ServerAccessGate), so a sign-in the user backs out of doesn't use it up.
 */
class SignInToServer @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards ServerAuthentication>,
    private val analytics: ServerSignInAnalytics,
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
                analytics.onServerConnected(type)
                if (rememberLogin) authentication.rememberLogin(login)
                Result.Success
            },
            onFailure = { error ->
                Timber.e("$type authentication failed. Error ${error.localizedMessage}")
                Result.Failure(error.userDescription())
            },
        )
    }
}
