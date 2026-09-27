package com.simplecityapps.provider.plex

/** The Plex sync's own message, beside the ServerStrings every server shares; each platform reads it from its own resources. */
interface PlexStrings {
    /** The server has no library titled "Music". */
    val musicLibraryMissing: String
}
