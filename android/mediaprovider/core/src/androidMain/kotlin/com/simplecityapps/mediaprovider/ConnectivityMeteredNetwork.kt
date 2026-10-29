package com.simplecityapps.mediaprovider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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
        return isMetered(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
    }

    override val meteredFlow: Flow<Boolean> = callbackFlow {
        val connectivityManager = connectivityManager
        if (connectivityManager == null) {
            trySend(true)
            awaitClose {}
            return@callbackFlow
        }
        // With no default network no callback ever fires, so the current value goes first.
        trySend(isMetered())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(isMetered(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(true)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun isMetered(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) return true
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
    }
}
