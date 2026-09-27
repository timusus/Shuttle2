package com.simplecityapps.shuttle.format

/**
 * "m:ss", or "h:mm:ss" from an hour: how the seek bar and queue rows show a time.
 * @param zeroValue returned instead, if given, when [ms] is 0.
 * @param padded space-pads the leading hour/minute to two digits (the detail-screen song rows rely on it).
 */
fun formatDuration(
    ms: Long,
    zeroValue: String? = null,
    padded: Boolean = false,
): String {
    if (ms == 0L && zeroValue != null) {
        return zeroValue
    }
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    val leading = if (hours > 0) hours else minutes
    val leadingText = if (padded) leading.toString().padStart(2, ' ') else leading.toString()
    val secondsText = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$leadingText:${minutes.toString().padStart(2, '0')}:$secondsText"
    } else {
        "$leadingText:$secondsText"
    }
}
