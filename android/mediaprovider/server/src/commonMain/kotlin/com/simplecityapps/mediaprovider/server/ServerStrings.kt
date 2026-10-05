package com.simplecityapps.mediaprovider.server

/** The messages a server sync reports, in the user's language; each platform reads them from its own resources. */
interface ServerStrings {
    /** No server address has been set. */
    val addressMissing: String

    /** Signing in to the server failed. */
    val authenticationError: String

    /** The server has libraries, but none that could hold music. */
    val musicLibraryMissing: String

    /** Stands in for a name the server didn't send, such as a playlist's. */
    val unknownName: String
}
