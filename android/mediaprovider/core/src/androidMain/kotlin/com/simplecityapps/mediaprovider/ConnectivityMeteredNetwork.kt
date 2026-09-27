package com.simplecityapps.mediaprovider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.Inject

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
