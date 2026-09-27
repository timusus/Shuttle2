package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.mediaprovider.server.QuickConnectCode
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.networking.userDescription
import dev.zacsweers.metro.Inject

class JellyfinQuickConnectAuthentication @Inject constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
) : QuickConnectAuthentication {
    override suspend fun isEnabled(address: String): Boolean = authenticationManager.isQuickConnectEnabled(address)

    override suspend fun initiate(address: String): Result<QuickConnectCode> = authenticationManager.initiateQuickConnect(address).userFriendly()

    override suspend fun poll(address: String, secret: String): Result<QuickConnectPollState> = authenticationManager.pollQuickConnect(address, secret).userFriendly()

    override suspend fun authenticate(address: String, secret: String): Result<Unit> {
        authenticationManager.setAddress(address)
        return authenticationManager.authenticateWithQuickConnect(address, secret).map { }.userFriendly()
    }

    private fun <T> Result<T>.userFriendly(): Result<T> = recoverCatching { error -> throw Exception(error.userDescription(), error) }
}
