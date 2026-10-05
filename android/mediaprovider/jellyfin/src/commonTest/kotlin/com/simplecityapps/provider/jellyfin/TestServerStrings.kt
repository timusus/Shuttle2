package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.ServerStrings

/** [ServerStrings] for tests, which assert on these rather than on a platform's string resources. */
object TestServerStrings : ServerStrings {
    override val addressMissing = "No server address"

    override val authenticationError = "Signing in failed"

    override val musicLibraryMissing = "No music library"

    override val unknownName = "Unknown"
}
