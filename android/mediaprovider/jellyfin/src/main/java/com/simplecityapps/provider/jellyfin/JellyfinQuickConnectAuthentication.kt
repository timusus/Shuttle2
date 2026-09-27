package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.mediaprovider.server.QuickConnectCode as SharedQuickConnectCode
import com.simplecityapps.mediaprovider.server.QuickConnectPollState as SharedQuickConnectPollState
import com.simplecityapps.networking.userDescription
import javax.inject.Inject

class JellyfinQuickConnectAuthentication @Inject constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
) : QuickConnectAuthentication {
    override suspend fun isEnabled(address: String): Boolean = authenticationManager.isQuickConnectEnabled(address)

    override suspend fun initiate(address: String): Result<SharedQuickConnectCode> = authenticationManager.initiateQuickConnect(address).fold(
        onSuccess = { Result.success(SharedQuickConnectCode(it.code, it.secret)) },
        onFailure = { error -> Result.failure(Exception(error.userDescription(), error)) },
    )

    override suspend fun poll(address: String, secret: String): Result<SharedQuickConnectPollState> = authenticationManager.pollQuickConnect(address, secret).fold(
        onSuccess = {
            Result.success(
                when (it) {
                    QuickConnectPollState.Pending -> SharedQuickConnectPollState.Pending
                    QuickConnectPollState.Authenticated -> SharedQuickConnectPollState.Authenticated
                    QuickConnectPollState.Denied -> SharedQuickConnectPollState.Denied
                },
            )
        },
        onFailure = { error -> Result.failure(Exception(error.userDescription(), error)) },
    )

    override suspend fun authenticate(address: String, secret: String): Result<Unit> {
        authenticationManager.setAddress(address)
        return authenticationManager.authenticateWithQuickConnect(address, secret).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error -> Result.failure(Exception(error.userDescription(), error)) },
        )
    }
}
