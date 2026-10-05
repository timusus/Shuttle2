package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.sources.servers.LocalNetworkAccess

/** Needs the permission, while [enforced] and not [granted], for a search of the network and for any address containing "192.168.". */
class FakeLocalNetworkAccess(var enforced: Boolean = false) : LocalNetworkAccess {
    var granted = false

    override fun needsRequest(address: String) = needsRequestToSearch() && "192.168." in address

    override fun needsRequestToSearch() = enforced && !granted
}
