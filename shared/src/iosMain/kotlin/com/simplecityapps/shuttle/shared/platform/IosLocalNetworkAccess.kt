package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.shuttle.ui.screens.sources.servers.LocalNetworkAccess
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** iOS asks for local network access itself, the first time the app dials a LAN address, so the sign-in never has to. */
@ContributesBinding(AppScope::class)
class IosLocalNetworkAccess @Inject constructor() : LocalNetworkAccess {
    override fun needsRequest(address: String) = false

    override fun needsRequestToSearch() = false
}
