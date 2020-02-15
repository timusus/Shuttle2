package com.simplecityapps.playback.equalizer

import kotlin.math.pow

fun Int.fromDb(): Double {
    return 10.0.pow(this / 20.0)
}