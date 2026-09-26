package com.simplecityapps.mediaprovider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.simplecityapps.shuttle.settings.StreamingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the active network costs the user money per byte (mobile data, a metered hotspot). */
fun interface MeteredNetwork {
    fun isMetered(): Boolean
}

/**
 * Metered unless the active network says otherwise, including 5G that's only temporarily unmetered (API 30+).
 * No network at all counts as metered, as [ConnectivityManager.isActiveNetworkMetered] does.
 */
class ConnectivityMeteredNetwork @Inject constructor(
    @ApplicationContext context: Context
) : MeteredNetwork {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isMetered(): Boolean {
        val connectivityManager = connectivityManager ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return true
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
    }
}

/**
 * The bitrate cap for a stream opened now: the metered or unmetered [StreamingSettings] quality, whichever network
 * is active. Read when a song's stream URL is built, so a network change applies from the next song.
 */
class StreamingBitrateCap @Inject constructor(
    private val streamingSettings: StreamingSettings,
    private val meteredNetwork: MeteredNetwork
) {
    /** The cap in kbps, or null to stream the original file. */
    fun maxBitrateKbps(): Int? {
        val quality = if (meteredNetwork.isMetered()) streamingSettings.meteredQuality else streamingSettings.unmeteredQuality
        return quality.value.maxBitrateKbps
    }
}
