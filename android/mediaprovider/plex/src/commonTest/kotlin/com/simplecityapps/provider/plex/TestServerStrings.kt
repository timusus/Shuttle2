package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.ServerStrings

/** [ServerStrings] for tests, which assert on these rather than on a platform's string resources. */
object TestServerStrings : ServerStrings {
    override val addressMissing = "No server address"

    override val queryingApi = "Querying the server"

    override val authenticationError = "Signing in failed"

    override val unknownName = "Unknown"
}

/** [PlexStrings] for tests. */
object TestPlexStrings : PlexStrings {
    override val musicLibraryMissing = "No music library"
}
