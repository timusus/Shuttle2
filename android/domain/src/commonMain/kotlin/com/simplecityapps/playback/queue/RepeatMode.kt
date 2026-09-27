package com.simplecityapps.playback.queue

/** The player's repeat mode. */
enum class RepeatMode {
    Off,
    All,
    One
    ;

    companion object {
        fun init(ordinal: Int): RepeatMode = when (ordinal) {
            All.ordinal -> All
            One.ordinal -> One
            Off.ordinal -> Off
            else -> Off
        }
    }
}
