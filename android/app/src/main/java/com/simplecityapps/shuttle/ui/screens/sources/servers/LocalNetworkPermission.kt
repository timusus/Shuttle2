package com.simplecityapps.shuttle.ui.screens.sources.servers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * Android 17's local-network permission: at targetSdk 37 any connection to a LAN address needs it at runtime.
 * Below that target the system grants it implicitly with INTERNET, and the docs say not to request it, so every
 * check here is a no-op until targetSdk reaches [GATING_SDK].
 */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"
    const val GATING_SDK = 37

    /** Whether the permission is enforced for this app: running on Android 17+ and targeting it. */
    fun isEnforced(context: Context): Boolean = isEnforced(Build.VERSION.SDK_INT, context.applicationInfo.targetSdkVersion)

    fun isEnforced(deviceSdk: Int, targetSdk: Int): Boolean = deviceSdk >= GATING_SDK && targetSdk >= GATING_SDK

    fun isGranted(context: Context): Boolean = ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether signing in to [typedAddress] on a [type] server should first ask for the permission. Plex signs in
     * through plex.tv, so its address isn't dialled at sign-in.
     */
    fun shouldRequest(
        type: MediaProviderType,
        typedAddress: String,
        enforced: Boolean,
        granted: Boolean,
    ): Boolean = enforced && !granted && type != MediaProviderType.Plex && isLocalAddress(typedAddress)

    /**
     * Whether the typed server address names a host on the local network: a private, link-local or CGNAT IPv4 or
     * IPv6 literal, `localhost`, a `.local`/`.lan`/`.home.arpa` name, or a bare name with no dot. A public name that
     * resolves to a LAN address can't be told apart without a lookup, so it isn't counted.
     */
    fun isLocalAddress(typedAddress: String): Boolean {
        val authority = serverAddress(typedAddress)?.substringAfter("://")?.substringBefore('/') ?: return false
        val host = (if (authority.startsWith('[')) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':')).lowercase()
        if (host.isEmpty()) return false
        if (':' in host) return isLocalIpv6(host)
        ipv4Octets(host)?.let { return isLocalIpv4(it) }
        return '.' !in host.trimEnd('.') || LOCAL_SUFFIXES.any { host.trimEnd('.').endsWith(it) }
    }

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

    private fun ipv4Octets(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.map { part -> part.takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }?.toInt()?.takeIf { it <= 255 } ?: return null }
    }

    private fun isLocalIpv4(o: List<Int>): Boolean = when {
        o[0] == 10 -> true
        o[0] == 127 -> true
        o[0] == 172 && o[1] in 16..31 -> true
        o[0] == 192 && o[1] == 168 -> true
        o[0] == 169 && o[1] == 254 -> true
        o[0] == 100 && o[1] in 64..127 -> true
        else -> false
    }

    private fun isLocalIpv6(host: String): Boolean {
        val h = host.substringBefore('%')
        if (h == "::1") return true
        val first = h.substringBefore(':').toIntOrNull(16) ?: return false
        // fe80::/10 link-local, fc00::/7 unique local
        return first in 0xfe80..0xfebf || first in 0xfc00..0xfdff
    }
}
