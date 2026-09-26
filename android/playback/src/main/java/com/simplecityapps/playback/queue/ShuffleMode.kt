package com.simplecityapps.playback.queue

/** Whether the queue plays, and is presented, in its shuffled order: the player's shuffle mode. */
enum class ShuffleMode {
    Off,
    On
    ;

    companion object {
        fun init(ordinal: Int): ShuffleMode = when (ordinal) {
            On.ordinal -> On
            Off.ordinal -> Off
            else -> Off
        }
    }
}

internal fun Boolean.toShuffleMode(): ShuffleMode = if (this) ShuffleMode.On else ShuffleMode.Off
