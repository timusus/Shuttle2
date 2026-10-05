package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.PinAuthentication
import com.simplecityapps.mediaprovider.server.SignInPin
import com.simplecityapps.networking.userDescription
import dev.zacsweers.metro.Inject

/** Plex's sign-in: a plex.tv PIN the user approves, then one of the account's servers. */
class PlexPinAuthentication @Inject constructor(
    private val authenticationManager: PlexAuthenticationManager,
) : PinAuthentication {
    override suspend fun createPin(): Result<SignInPin> = authenticationManager.createPin().userFriendly()

    override suspend fun checkPin(pin: SignInPin): Result<String?> = authenticationManager.checkPin(pin).userFriendly()

    override suspend fun servers(accountToken: String): Result<List<AccountServer>> = authenticationManager.servers(accountToken).userFriendly()

    override fun isSignedInTo(server: AccountServer): Boolean = authenticationManager.isSignedInTo(server)

    // Its failure already says what went wrong, in the user's terms
    override suspend fun connect(server: AccountServer): Result<Unit> = authenticationManager.connect(server)

    private fun <T> Result<T>.userFriendly(): Result<T> = recoverCatching { error -> throw Exception(error.userDescription(), error) }
}
