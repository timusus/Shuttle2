package com.simplecityapps.shuttle.ui.screens.sources.servers

/**
 * The server address [typed] as the sign-in uses it: trimmed, `http://` added when it has no scheme, and without
 * trailing slashes. An IPv6 host may come bracketed (`[fe80::1]:8096`) or bare (`fe80::1`, wrapped in brackets here,
 * so it can't carry a port). Null when there's no host to connect to (empty, only a scheme, an unclosed bracket or
 * with a space in it).
 */
fun serverAddress(typed: String): String? {
    val trimmed = typed.trim()
    if (trimmed.any(Char::isWhitespace)) return null
    val scheme = if ("://" in trimmed) trimmed.substringBefore("://") else "http"
    val rest = trimmed.substringAfter("://").trimEnd('/')
    val authority = rest.substringBefore('/')
    val path = rest.removePrefix(authority)
    val hostAndPort =
        when {
            // Bracketed IPv6, then an optional port
            authority.startsWith('[') -> {
                val host = authority.substringBefore(']', missingDelimiterValue = "").removePrefix("[")
                val afterHost = authority.substringAfter(']', missingDelimiterValue = "")
                authority.takeIf { host.isNotEmpty() && (afterHost.isEmpty() || afterHost.startsWith(':')) }
            }

            // Bare IPv6: no room for a port, so all of it is the host
            authority.count { it == ':' } >= 2 -> "[$authority]"

            else -> authority.takeIf { authority.substringBefore(':').isNotEmpty() }
        }
    return hostAndPort?.let { "$scheme://$it$path" }
}

/**
 * Whether two server addresses name the same server: the host compared case-insensitively, then the port and path.
 * The scheme (`http` → `https`), a default port and trailing slashes don't count.
 */
fun isSameServerAddress(a: String, b: String): Boolean = serverIdentity(a)?.let { it == serverIdentity(b) } ?: false

private fun serverIdentity(address: String): String? {
    val normalised = serverAddress(address) ?: return null
    val scheme = normalised.substringBefore("://").lowercase()
    val rest = normalised.substringAfter("://")
    val authority = rest.substringBefore('/')
    val path = rest.removePrefix(authority)
    val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    val port = authority.removePrefix(host).removePrefix(":")
    val defaultPort = when (scheme) {
        "http" -> "80"
        "https" -> "443"
        else -> null
    }
    return "${host.lowercase()}:${port.takeUnless { it == defaultPort }.orEmpty()}$path"
}
