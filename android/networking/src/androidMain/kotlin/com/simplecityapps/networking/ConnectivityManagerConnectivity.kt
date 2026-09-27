package com.simplecityapps.networking

import android.net.ConnectivityManager

/** [NetworkConnectivity] from the system's [ConnectivityManager]; never connected without one. */
class ConnectivityManagerConnectivity(
    private val connectivityManager: ConnectivityManager?
) : NetworkConnectivity {
    @Suppress("DEPRECATION")
    override fun isConnected(): Boolean = connectivityManager?.activeNetworkInfo?.isConnected ?: false
}
