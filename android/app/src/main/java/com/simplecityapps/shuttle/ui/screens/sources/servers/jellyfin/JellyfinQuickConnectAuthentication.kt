package com.simplecityapps.shuttle.ui.screens.sources.servers.jellyfin

import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.jellyfin.QuickConnectPollState as JellyfinQuickConnectPollState
import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectAuthentication
import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectCode
import com.simplecityapps.shuttle.ui.screens.sources.servers.QuickConnectPollState
import javax.inject.Inject

class JellyfinQuickConnectAuthentication @Inject constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
) : QuickConnectAuthentication {
    override suspend fun isEnabled(address: String): Boolean = authenticationManager.isQuickConnectEnabled(address)

    override suspend fun initiate(address: String): Result<QuickConnectCode> = authenticationManager.initiateQuickConnect(address).map { QuickConnectCode(it.code, it.secret) }

    override suspend fun poll(address: String, secret: String): Result<QuickConnectPollState> = authenticationManager.pollQuickConnect(address, secret).map {
        when (it) {
            JellyfinQuickConnectPollState.Pending -> QuickConnectPollState.Pending
            JellyfinQuickConnectPollState.Authenticated -> QuickConnectPollState.Authenticated
            JellyfinQuickConnectPollState.Denied -> QuickConnectPollState.Denied
        }
    }

    override suspend fun authenticate(address: String, secret: String): Result<Unit> {
        authenticationManager.setAddress(address)
        return authenticationManager.authenticateWithQuickConnect(address, secret).map {}
    }
}
