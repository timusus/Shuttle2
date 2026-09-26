package com.simplecityapps.playback.queue

import androidx.media3.common.Player

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

fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}

fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.One -> Player.REPEAT_MODE_ONE
}
