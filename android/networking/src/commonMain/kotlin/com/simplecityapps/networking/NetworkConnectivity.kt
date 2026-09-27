package com.simplecityapps.networking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.util.AttributeKey

/** Whether the device has a network connection, which tells "offline" apart from "the server is unreachable". */
fun interface NetworkConnectivity {
    fun isConnected(): Boolean
}

class NetworkConnectivityConfig {
    var connectivity: NetworkConnectivity? = null
}

private val ConnectivityKey = AttributeKey<NetworkConnectivity>("NetworkConnectivity")

/** Makes a client's [NetworkConnectivity] available to [networkResult], which reports it on a failed request. */
val NetworkConnectivityPlugin = createClientPlugin("NetworkConnectivity", ::NetworkConnectivityConfig) {
    pluginConfig.connectivity?.let { connectivity -> client.attributes.put(ConnectivityKey, connectivity) }
}

/** Whether the device is connected, as this client's [NetworkConnectivity] reports it; false when it has none. */
internal val HttpClient.isConnected: Boolean
    get() = attributes.getOrNull(ConnectivityKey)?.isConnected() ?: false
