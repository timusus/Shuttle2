package com.simplecityapps.playback.equalizer

import kotlin.math.pow

fun Double.fromDb(): Double {
    return 10.0.pow(this / 20.0)
}

fun Float.fromDb(): Float {
    return 10f.pow(this / 20f)
}