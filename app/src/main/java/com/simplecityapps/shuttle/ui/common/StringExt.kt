package com.simplecityapps.shuttle.ui.common

import java.util.concurrent.TimeUnit

fun Long.toHms(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours == 0L) {
        String.format("%2d:%02d", minutes, seconds)
    } else {
        String.format("%2d:%02d:%02d", hours, minutes, seconds)
    }
}