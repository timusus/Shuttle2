package com.simplecityapps.shuttle.ui.common.utils

import java.util.concurrent.TimeUnit

/**
 * @param defaultValue returned if specified, and the duration being converted is 0 seconds.
 */
fun Int.toHms(defaultValue: String? = null): String {

    if (this == 0 && defaultValue != null) {
        return defaultValue
    }

    val hours = TimeUnit.MILLISECONDS.toHours(this.toLong())
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this.toLong()) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this.toLong()) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours == 0L) {
        String.format("%2d:%02d", minutes, seconds)
    } else {
        String.format("%2d:%02d:%02d", hours, minutes, seconds)
    }
}
