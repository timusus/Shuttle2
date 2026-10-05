package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.MediaProviderType

/** A server that answered on the local network: what to show for it, and the [address] to sign in at. */
data class DiscoveredServer(
    val name: String,
    val address: String,
)

/**
 * Looks for servers on the local network, to offer their addresses as one-tap suggestions in the sign-in form. Never
 * fails: no network, no answer, or a type it can't look for all come back empty.
 */
interface ServerDiscovery {
    suspend fun discover(type: MediaProviderType): List<DiscoveredServer>
}
