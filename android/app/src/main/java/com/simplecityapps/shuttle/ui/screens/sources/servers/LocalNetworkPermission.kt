package com.simplecityapps.shuttle.ui.screens.sources.servers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
     * Whether the typed server address names a host on the local network, so reaching it needs the permission: a
     * private, link-local or CGNAT IPv4 or IPv6 literal (an IPv4 one may also be `::ffff:`-mapped), a `.local`, `.lan`,
     * `.home.arpa` or `.internal` name, or a single-label name such as `nas` (resolved by mDNS or the LAN's DNS).
     * Loopback (`localhost`, 127/8, `::1`) never leaves the device, so it needs nothing. A public name that resolves
     * to a LAN address can't be told apart without a lookup, so it isn't counted.
     */
    fun isLocalAddress(typedAddress: String): Boolean {
        val authority = serverAddress(typedAddress)?.substringAfter("://")?.substringBefore('/') ?: return false
        val host = (if (authority.startsWith('[')) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':')).lowercase()
        if (':' in host) return isLocalIpv6(host)
        ipv4Octets(host)?.let { return isLocalIpv4(it) }
        val name = host.trimEnd('.')
        if (name.isEmpty() || name == "localhost") return false
        return '.' !in name || LOCAL_SUFFIXES.any { name.endsWith(it) }
    }

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

    private fun ipv4Octets(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.map { part -> part.takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }?.toInt()?.takeIf { it <= 255 } ?: return null }
    }

    private fun isLocalIpv4(o: List<Int>): Boolean = when {
        o[0] == 10 -> true
        o[0] == 172 && o[1] in 16..31 -> true
        o[0] == 192 && o[1] == 168 -> true
        o[0] == 169 && o[1] == 254 -> true
        o[0] == 100 && o[1] in 64..127 -> true
        else -> false
    }

    private fun isLocalIpv6(host: String): Boolean {
        val h = host.substringBefore('%')
        mappedIpv4(h)?.let { return isLocalIpv4(it) }
        val first = h.substringBefore(':').toIntOrNull(16) ?: return false
        // fe80::/10 link-local, fc00::/7 unique local
        return first in 0xfe80..0xfebf || first in 0xfc00..0xfdff
    }

    /** The IPv4 address inside an IPv4-mapped IPv6 one (`::ffff:192.168.1.5` or `::ffff:c0a8:105`), if it is one. */
    private fun mappedIpv4(host: String): List<Int>? {
        val tail = host.removePrefix("::ffff:").takeIf { it != host } ?: return null
        ipv4Octets(tail)?.let { return it }
        val groups = tail.split(':').map { it.toIntOrNull(16)?.takeIf { g -> g in 0..0xffff } ?: return null }
        if (groups.size != 2) return null
        return listOf(groups[0] shr 8, groups[0] and 0xff, groups[1] shr 8, groups[1] and 0xff)
    }
}
