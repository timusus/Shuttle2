package com.simplecityapps.shuttle.server

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A request header the user added for a server, sent with every request to it: a reverse proxy's access token, say. */
data class CustomHeader(
    val name: String,
    val value: String
) {
    /**
     * A header every HTTP stack sends as it is: a name that's an RFC 7230 token (no spaces, colons or control
     * characters) and a value of printable ASCII and tabs, so on one line. OkHttp throws on anything else.
     */
    val isValid: Boolean
        get() = name.isNotEmpty() && name.all { it in TOKEN_CHARACTERS } && value.all { it == '\t' || it in ' '..'~' }

    private companion object {
        val TOKEN_CHARACTERS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "!#$%&'*+-.^_`|~".toList()
    }
}

/** How requests to one server are made beyond its credentials. */
data class ServerConnection(
    val headers: List<CustomHeader> = emptyList(),
    /** The SHA-256 fingerprint (hex, no separators) of the one certificate the user trusted for the server, if any. */
    val trustedCertificate: String? = null
) {
    fun trusts(fingerprint: String): Boolean = trustedCertificate != null && trustedCertificate == normalizeFingerprint(fingerprint)

    companion object {
        val None = ServerConnection()
    }
}

/**
 * Each media server's custom headers and trusted certificate, keyed by its origin (host and port, [ServerOrigin]), so
 * every request to that server carries them: API calls, streams, artwork and downloads (#894). Kept in
 * [SecurePreferenceManager] with the servers' credentials, and cached in memory, as every request looks them up.
 *
 * A certificate is trusted for one origin, by its exact fingerprint; verification is never turned off. A certificate the
 * platform refused is remembered (in memory only) by [recordRejectedCertificate], for the sign-in to offer to trust it.
 */
@SingleIn(AppScope::class)
class ServerConnectionStore @Inject constructor(
    private val securePreferenceManager: SecurePreferenceManager
) {
    private val cache = MutableStateFlow<Map<ServerOrigin, ServerConnection>>(emptyMap())
    private val rejected = MutableStateFlow<Map<ServerOrigin, String>>(emptyMap())

    /** The connection settings for requests to [origin]: [ServerConnection.None] for a server with none. */
    fun connection(origin: ServerOrigin): ServerConnection {
        cache.value[origin]?.let { return it }
        val connection =
            ServerConnection(
                headers = decodeHeaders(securePreferenceManager.getString(headersKey(origin))),
                trustedCertificate = securePreferenceManager.getString(certificateKey(origin))
            )
        cache.update { it + (origin to connection) }
        return connection
    }

    /** The connection settings for the server at [address], or [ServerConnection.None] when it isn't an address. */
    fun connection(address: String): ServerConnection = ServerOrigin.parse(address)?.let(::connection) ?: ServerConnection.None

    /** Replaces the custom headers sent to [origin]; invalid and blank ones are dropped. */
    fun setHeaders(
        origin: ServerOrigin,
        headers: List<CustomHeader>
    ) {
        val valid = headers.map { CustomHeader(it.name.trim(), it.value.trim()) }.filter { it.isValid }
        securePreferenceManager.putString(headersKey(origin), encodeHeaders(valid))
        cache.update { it + (origin to connection(origin).copy(headers = valid)) }
    }

    /** Trusts the certificate with [fingerprint] for [origin] alone, in place of any it trusted before. */
    fun trustCertificate(
        origin: ServerOrigin,
        fingerprint: String
    ) {
        val normalized = normalizeFingerprint(fingerprint)
        securePreferenceManager.putString(certificateKey(origin), normalized)
        cache.update { it + (origin to connection(origin).copy(trustedCertificate = normalized)) }
        rejected.update { it - origin }
    }

    /** Forgets [origin]'s headers and trusted certificate, as removing its server does. */
    fun forget(origin: ServerOrigin) {
        securePreferenceManager.putString(headersKey(origin), null)
        securePreferenceManager.putString(certificateKey(origin), null)
        cache.update { it - origin }
        rejected.update { it - origin }
    }

    /** Remembers that the platform refused [origin]'s certificate, with [fingerprint], for [rejectedCertificate]. */
    fun recordRejectedCertificate(
        origin: ServerOrigin,
        fingerprint: String
    ) {
        rejected.update { it + (origin to normalizeFingerprint(fingerprint)) }
    }

    /**
     * Whether to accept a certificate the platform refused for [origin]: only the one with [fingerprint] the user
     * trusted for it. Otherwise it's remembered as [rejectedCertificate], for the sign-in to offer to trust it.
     */
    fun acceptRefusedCertificate(
        origin: ServerOrigin,
        fingerprint: String
    ): Boolean {
        if (connection(origin).trusts(fingerprint)) return true
        recordRejectedCertificate(origin, fingerprint)
        return false
    }

    /** The fingerprint of the certificate [origin] last presented and the platform refused, if it did. */
    fun rejectedCertificate(origin: ServerOrigin): String? = rejected.value[origin]

    fun clearRejectedCertificate(origin: ServerOrigin) {
        rejected.update { it - origin }
    }

    private fun headersKey(origin: ServerOrigin) = "server_connection_${origin}_headers"

    private fun certificateKey(origin: ServerOrigin) = "server_connection_${origin}_certificate"

    private companion object {
        // One header per line, as `name: value`: a valid header holds neither a line break nor a colon in its name
        fun encodeHeaders(headers: List<CustomHeader>): String? = headers.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.name}: ${it.value}" }

        fun decodeHeaders(encoded: String?): List<CustomHeader> = encoded
            ?.lines()
            ?.mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else CustomHeader(line.substring(0, colon), line.substring(colon + 1).trim())
            }?.filter { it.isValid }
            .orEmpty()
    }
}

/** A server's host and port: what its headers and trusted certificate apply to. */
data class ServerOrigin(
    val host: String,
    val port: Int
) {
    override fun toString() = "$host:$port"

    companion object {
        fun of(
            host: String,
            port: Int
        ) = ServerOrigin(host.lowercase().removePrefix("[").removeSuffix("]"), port)

        /** The origin of [address] (`https://music.example.com:8920/jellyfin`, say), or null when it has no host. */
        fun parse(address: String): ServerOrigin? {
            val trimmed = address.trim()
            val schemeEnd = trimmed.indexOf("://")
            val scheme = if (schemeEnd >= 0) trimmed.substring(0, schemeEnd).lowercase() else "http"
            val rest = if (schemeEnd >= 0) trimmed.substring(schemeEnd + 3) else trimmed
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
            if (authority.isEmpty()) return null
            val (host, port) =
                if (authority.startsWith("[")) {
                    val close = authority.indexOf(']')
                    if (close < 0) return null
                    authority.substring(1, close) to authority.substring(close + 1).removePrefix(":")
                } else {
                    authority.substringBefore(':') to authority.substringAfter(':', "")
                }
            if (host.isEmpty()) return null
            val defaultPort = if (scheme == "https") 443 else 80
            return of(host, if (port.isEmpty()) defaultPort else port.toIntOrNull() ?: return null)
        }
    }
}

/** [fingerprint] as the store keeps it: upper-case hex without separators. */
fun normalizeFingerprint(fingerprint: String): String = fingerprint.filter { it.isLetterOrDigit() }.uppercase()

/** [fingerprint] as the user reads it: colon-separated pairs of upper-case hex digits. */
fun displayFingerprint(fingerprint: String): String = normalizeFingerprint(fingerprint).chunked(2).joinToString(":")

/** [digest] (a certificate's SHA-256) as a fingerprint: upper-case hex without separators. */
fun fingerprintOf(digest: ByteArray): String = digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
