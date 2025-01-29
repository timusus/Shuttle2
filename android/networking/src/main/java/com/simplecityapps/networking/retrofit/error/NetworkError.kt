package com.simplecityapps.networking.retrofit.error

/**
 * Represents an error in which the server could not be reached.
 */
class NetworkError(val hasInternetConnectivity: Boolean, val t: Throwable) : Error() {
    override fun toString(): String = "NetworkError (hasInternetConnectivity: $hasInternetConnectivity, error: ${t.localizedMessage})"
}
