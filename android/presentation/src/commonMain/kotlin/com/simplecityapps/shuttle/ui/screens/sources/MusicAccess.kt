package com.simplecityapps.shuttle.ui.screens.sources

/** Where the music permission stands. */
enum class MusicAccess {
    NotRequested,

    /** Refused, but the system will still show the prompt again. */
    Denied,

    /** Refused for good: only the app's system settings page can grant it now. */
    PermanentlyDenied,
    Granted;

    companion object {
        fun of(granted: Boolean, requested: Boolean, showRationale: Boolean): MusicAccess = when {
            granted -> Granted
            !requested -> NotRequested
            showRationale -> Denied
            else -> PermanentlyDenied
        }
    }
}
