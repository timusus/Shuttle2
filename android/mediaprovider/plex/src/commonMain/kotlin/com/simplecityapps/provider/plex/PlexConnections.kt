package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.ServerConnection
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * [connections] in the order a sign-in tries them: on the local network before across the internet, the relay
 * (bandwidth-capped) last, and HTTPS before plain HTTP within each.
 */
internal fun orderConnections(connections: List<ServerConnection>): List<ServerConnection> = connections.sortedWith(
    compareBy<ServerConnection> { it.relay }
        .thenBy { !it.local }
        .thenBy { !it.uri.startsWith("https://", ignoreCase = true) }
)

/**
 * The uri of the first of [connections], in [orderConnections]' order, that [probe] says answers, or null when none
 * does. The probes run at once, so a dead address costs one timeout rather than one each; the rest are cancelled as
 * soon as the answer is known.
 */
internal suspend fun firstReachable(
    connections: List<ServerConnection>,
    probe: suspend (uri: String) -> Boolean
): String? = coroutineScope {
    val probes = orderConnections(connections).map { connection -> connection.uri to async { probe(connection.uri) } }
    try {
        probes.firstOrNull { (_, reachable) -> reachable.await() }?.first
    } finally {
        probes.forEach { (_, reachable) -> reachable.cancel() }
    }
}

/** Whether [address] (as the user typed it, or a saved uri) names this connection: its uri's host or its plain IP, on its port. */
internal fun ServerConnection.matches(address: String): Boolean {
    val saved = runCatching { Url(address) }.getOrNull() ?: return false
    val uri = runCatching { Url(uri) }.getOrNull()
    val hosts = listOfNotNull(uri?.host, this.address)
    val ports = listOfNotNull(uri?.port, port)
    return hosts.any { it.equals(saved.host, ignoreCase = true) } && saved.port in ports
}
