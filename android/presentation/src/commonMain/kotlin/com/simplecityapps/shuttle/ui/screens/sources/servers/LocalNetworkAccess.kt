package com.simplecityapps.shuttle.ui.screens.sources.servers

/**
 * Whether reaching the local network first needs the user's permission (Android 17's local-network permission, once
 * an app targets it). The sign-in asks before it dials a LAN address or looks for servers there. Never needed on iOS
 * here, nor on Android below that target.
 */
interface LocalNetworkAccess {
    /** True when the permission is enforced, not yet granted, and [address] is on the local network. */
    fun needsRequest(address: String): Boolean

    /** True when the permission is enforced and not yet granted, for a search of the local network itself. */
    fun needsRequestToSearch(): Boolean
}
