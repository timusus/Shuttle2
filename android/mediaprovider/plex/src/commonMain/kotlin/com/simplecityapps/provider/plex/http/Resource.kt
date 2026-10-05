package com.simplecityapps.provider.plex.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Something on a plex.tv account: a server, a player or the like, as [provides] says (a comma-separated list, with
 * `server` for a Plex Media Server). [accessToken] is the token the device takes from this account.
 */
@Serializable
data class Resource(
    val name: String = "",
    val clientIdentifier: String,
    val provides: String = "",
    val owned: Boolean = false,
    val accessToken: String? = null,
    val connections: List<Connection> = emptyList()
) {
    val isServer: Boolean get() = provides.split(',').any { it.trim() == "server" }
}

/** One of a [Resource]'s addresses: [uri] is the whole of it, [address] and [port] the IP and port behind it. */
@Serializable
data class Connection(
    val uri: String,
    val protocol: String? = null,
    val address: String? = null,
    val port: Int? = null,
    val local: Boolean = false,
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false
)
