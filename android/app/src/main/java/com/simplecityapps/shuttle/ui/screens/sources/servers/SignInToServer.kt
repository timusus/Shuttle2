package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.networking.userDescription
import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import timber.log.Timber

/**
 * Signs in to a [type] server with [login][invoke]. Once it's in, the trial hears of it, and the login is saved for
 * next time if the user asked to remember it.
 */
class SignInToServer @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards ServerAuthentication>,
    private val serverTrial: ServerTrial,
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
                serverTrial.onServerConnected(type)
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
