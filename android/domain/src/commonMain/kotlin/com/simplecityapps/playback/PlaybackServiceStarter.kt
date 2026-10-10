package com.simplecityapps.playback

/** A command that system entry points (widgets, shortcuts, the quick settings tile) send to the playback service. */
enum class PlaybackServiceAction {
    TogglePlayback,
    SkipPrevious,
    SkipNext,
    ToggleShuffle,
    ToggleRepeat,
    ShuffleAll
}

/** How UI entry points start the playback service with a command, in the foreground. */
interface PlaybackServiceStarter {
    /** A start the system refuses (a foreground start from the background) is logged, not thrown. */
    fun start(action: PlaybackServiceAction)
}
