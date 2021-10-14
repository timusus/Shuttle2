package com.simplecityapps.shuttle.error

/**
 * Represents an error in which the server could not be reached.
 */
class NetworkError(
    val hasInternetConnectivity: Boolean,
    override val cause: Throwable
) : Throwable(cause) {

    override fun toString(): String {
        return "NetworkError (hasInternetConnectivity: ${hasInternetConnectivity}, error: ${cause.message})"
    }
}