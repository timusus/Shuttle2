package com.simplecityapps.shuttle.server

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** A request header the user added for a server, sent with every request to it: a reverse proxy's access token, say. */
data class CustomHeader(
    val name: String,
    val value: String
) {
    /**
     * A header every HTTP stack sends as it is: a name that's an RFC 7230 token (no spaces, colons or control
     * characters) and a value of printable ASCII and tabs, so on one line. OkHttp throws on anything else. A
     * [reserved][isReserved] name is never valid.
     */
    val isValid: Boolean
        get() = name.isNotEmpty() && name.all { it in TOKEN_CHARACTERS } && !isReserved && value.all { it == '\t' || it in ' '..'~' }

    /**
     * A header the app sets itself, which a user's header must never replace: the request's framing (Host,
     * Content-Length and the like) or the server's credentials (Authorization, Jellyfin and Emby's token, Plex's).
     */
    val isReserved: Boolean
        get() = RESERVED_NAMES.any { it.equals(name, ignoreCase = true) }

    private companion object {
        val TOKEN_CHARACTERS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "!#$%&'*+-.^_`|~".toList()

        val RESERVED_NAMES =
            listOf(
                "Host",
                "Content-Length",
                "Content-Type",
                "Transfer-Encoding",
                "Connection",
                "Authorization",
                "X-Emby-Authorization",
                "X-Emby-Token",
                "X-MediaBrowser-Token",
                "X-Plex-Token"
            )
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
 *
 * Requests look settings up from any thread while the sign-in changes them. Each change writes the preferences first,
 * then updates the cache atomically from the cache as it is at that moment, counting the change ([Cache.changes]); a
 * read that misses loads the preferences inside its own atomic update. The count makes every change fail such an
 * update that started before it, even one that left the map as it was (forgetting a server that wasn't cached), so a
 * lookup never puts back settings a change has just replaced.
 */
@SingleIn(AppScope::class)
class ServerConnectionStore @Inject constructor(
    private val securePreferenceManager: SecurePreferenceManager
) {
    private val cache = MutableStateFlow(Cache())
    private val rejected = MutableStateFlow<Map<ServerOrigin, String>>(emptyMap())

    /** The connection settings for requests to [origin]: [ServerConnection.None] for a server with none. */
    fun connection(origin: ServerOrigin): ServerConnection {
        cache.value.connections[origin]?.let { return it }
        return cache.updateAndGet { it.loading(origin) }.connections.getValue(origin)
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
        cache.update { it.changing(origin) { connection -> connection.copy(headers = valid) } }
    }

    /** Trusts the certificate with [fingerprint] for [origin] alone, in place of any it trusted before. */
    fun trustCertificate(
        origin: ServerOrigin,
        fingerprint: String
    ) {
        val normalized = normalizeFingerprint(fingerprint)
        securePreferenceManager.putString(certificateKey(origin), normalized)
        cache.update { it.changing(origin) { connection -> connection.copy(trustedCertificate = normalized) } }
        rejected.update { it - origin }
    }

    /** Forgets [origin]'s headers and trusted certificate, as removing its server does. */
    fun forget(origin: ServerOrigin) {
        securePreferenceManager.putString(headersKey(origin), null)
        securePreferenceManager.putString(certificateKey(origin), null)
        cache.update { Cache(it.connections - origin, it.changes + 1) }
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

    /** The settings cached so far, and how many changes have been made: see the class's comment. */
    private data class Cache(
        val connections: Map<ServerOrigin, ServerConnection> = emptyMap(),
        val changes: Long = 0
    )

    /** This cache with [origin] in it, loaded from the preferences when it isn't: run inside an atomic update. */
    private fun Cache.loading(origin: ServerOrigin): Cache = if (origin in connections) {
        this
    } else {
        copy(
            connections = connections + (
                origin to
                    ServerConnection(
                        headers = decodeHeaders(securePreferenceManager.getString(headersKey(origin))),
                        trustedCertificate = securePreferenceManager.getString(certificateKey(origin))
                    )
                )
        )
    }

    /** This cache with [change] made to [origin]'s settings, counted: run inside an atomic update. */
    private fun Cache.changing(
        origin: ServerOrigin,
        change: (ServerConnection) -> ServerConnection
    ): Cache {
        val loaded = loading(origin)
        return Cache(loaded.connections + (origin to change(loaded.connections.getValue(origin))), changes + 1)
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
        /**
         * The origin of [host] and [port], with the host as OkHttp and the platform report it (lower case, an
         * internationalised name in punycode, without an IPv6 literal's brackets), so a server's settings are found
         * however its address was typed.
         */
        fun of(
            host: String,
            port: Int
        ) = ServerOrigin(asciiHost(host.removePrefix("[").removeSuffix("]")), port)

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

/** [host] in lower case, each label holding other than ASCII in punycode (`bücher.example` is `xn--bcher-kva.example`). */
internal fun asciiHost(host: String): String = host
    .lowercase()
    .split('.', '\u3002', '\uFF0E', '\uFF61')
    .joinToString(".") { label -> if (label.all { it.code < 0x80 }) label else "xn--" + punycode(label) }

/** [label] in punycode (RFC 3492), without the `xn--` prefix. */
private fun punycode(label: String): String {
    val codePoints = buildList {
        var i = 0
        while (i < label.length) {
            val c = label[i]
            if (c.isHighSurrogate() && i + 1 < label.length && label[i + 1].isLowSurrogate()) {
                add(((c.code - 0xD800) shl 10) + (label[i + 1].code - 0xDC00) + 0x10000)
                i += 2
            } else {
                add(c.code)
                i++
            }
        }
    }
    val output = StringBuilder()
    codePoints.filter { it < 0x80 }.forEach { output.append(it.toChar()) }
    val basicCount = output.length
    if (basicCount > 0) output.append('-')
    var n = PUNYCODE_INITIAL_N
    var delta = 0L
    var bias = PUNYCODE_INITIAL_BIAS
    var handled = basicCount
    while (handled < codePoints.size) {
        val next = codePoints.filter { it >= n }.min()
        delta += (next - n).toLong() * (handled + 1)
        n = next
        for (codePoint in codePoints) {
            if (codePoint < n) delta++
            if (codePoint == n) {
                var q = delta
                var k = PUNYCODE_BASE
                while (true) {
                    val t = (k - bias).coerceIn(PUNYCODE_T_MIN, PUNYCODE_T_MAX)
                    if (q < t) break
                    output.append(punycodeDigit(t + ((q - t) % (PUNYCODE_BASE - t)).toInt()))
                    q = (q - t) / (PUNYCODE_BASE - t)
                    k += PUNYCODE_BASE
                }
                output.append(punycodeDigit(q.toInt()))
                bias = punycodeBias(delta, handled + 1, first = handled == basicCount)
                delta = 0
                handled++
            }
        }
        delta++
        n++
    }
    return output.toString()
}

private fun punycodeBias(
    delta: Long,
    points: Int,
    first: Boolean
): Int {
    var d = if (first) delta / 700 else delta / 2
    d += d / points
    var k = 0
    while (d > ((PUNYCODE_BASE - PUNYCODE_T_MIN) * PUNYCODE_T_MAX) / 2) {
        d /= PUNYCODE_BASE - PUNYCODE_T_MIN
        k += PUNYCODE_BASE
    }
    return k + ((PUNYCODE_BASE - PUNYCODE_T_MIN + 1) * d / (d + 38)).toInt()
}

private fun punycodeDigit(digit: Int): Char = if (digit < 26) 'a' + digit else '0' + (digit - 26)

private const val PUNYCODE_BASE = 36
private const val PUNYCODE_T_MIN = 1
private const val PUNYCODE_T_MAX = 26
private const val PUNYCODE_INITIAL_N = 128
private const val PUNYCODE_INITIAL_BIAS = 72

/** [fingerprint] as the store keeps it: upper-case hex without separators. */
fun normalizeFingerprint(fingerprint: String): String = fingerprint.filter { it.isLetterOrDigit() }.uppercase()

/** [fingerprint] as the user reads it: colon-separated pairs of upper-case hex digits. */
fun displayFingerprint(fingerprint: String): String = normalizeFingerprint(fingerprint).chunked(2).joinToString(":")

/** [digest] (a certificate's SHA-256) as a fingerprint: upper-case hex without separators. */
fun fingerprintOf(digest: ByteArray): String = digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.uppercase()
