package com.simplecityapps.shuttle.debug.livelog

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private fun Int.priorityLabel(): String = when (this) {
    Log.VERBOSE -> "V"
    Log.DEBUG -> "D"
    Log.INFO -> "I"
    Log.WARN -> "W"
    Log.ERROR -> "E"
    Log.ASSERT -> "A"
    else -> "?"
}

/** One logcat-style line: `HH:mm:ss.SSS P/Tag: message`. */
fun LiveLogLine.format(): String {
    val tagPart = tag?.let { "$it: " }.orEmpty()
    return "${timeFormat.format(Date(timestampMillis))} ${priority.priorityLabel()}/$tagPart$message"
}

fun List<LiveLogLine>.formatAll(): String = joinToString(separator = "\n") { it.format() }
