package com.simplecityapps.shuttle.debug.livelog

/** One entry from [com.simplecityapps.shuttle.debug.DebugLoggingTree], kept for the Live log screen. */
data class LiveLogLine(
    val id: Long,
    val timestampMillis: Long,
    val priority: Int,
    val tag: String?,
    val message: String
)
