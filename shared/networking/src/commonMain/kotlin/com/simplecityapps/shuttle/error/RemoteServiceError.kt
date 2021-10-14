package com.simplecityapps.shuttle.error

/**
 * An error response from the server.
 */
open class RemoteServiceError : Throwable() {

    override fun toString(): String {
        return "RemoteServiceError(message: $message)"
    }
}