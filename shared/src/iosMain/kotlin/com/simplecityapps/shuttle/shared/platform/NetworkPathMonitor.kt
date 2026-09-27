package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MeteredNetwork
import com.simplecityapps.networking.NetworkConnectivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * The current network path, from `NWPathMonitor`: whether there is one ([NetworkConnectivity], so a request fails fast
 * offline), and whether it's metered ([MeteredNetwork]: cellular or a hotspot, or Low Data Mode). Until the first
 * update arrives it reports connected and unmetered, so a request made at launch is tried rather than refused.
 */
@SingleIn(AppScope::class)
class NetworkPathMonitor @Inject constructor() :
    NetworkConnectivity,
    MeteredNetwork {
    @Volatile
    private var connected = true

    @Volatile
    private var metered = false

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            connected = nw_path_get_status(path) == nw_path_status_satisfied
            metered = nw_path_is_expensive(path) || nw_path_is_constrained(path)
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.simplecityapps.shuttle.network-path", null))
        nw_path_monitor_start(monitor)
    }

    override fun isConnected(): Boolean = connected

    override fun isMetered(): Boolean = metered
}
