package com.simplecityapps.playback.queue

import androidx.media3.common.Player

// Conversions between the domain's shuffle and repeat modes and the Media3 player's.

internal fun Boolean.toShuffleMode(): ShuffleMode = if (this) ShuffleMode.On else ShuffleMode.Off

internal fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    else -> RepeatMode.Off
}

internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.All -> Player.REPEAT_MODE_ALL
    RepeatMode.One -> Player.REPEAT_MODE_ONE
}
