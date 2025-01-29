package com.simplecityapps.playback.dsp.equalizer

import kotlin.math.log10
import kotlin.math.pow

fun Double.fromDb(): Double = 10.0.pow(this / 20.0)

fun Double.toDb(): Double = 20 * log10(this)
