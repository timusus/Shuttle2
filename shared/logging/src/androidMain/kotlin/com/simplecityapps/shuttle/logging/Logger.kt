package com.simplecityapps.shuttle.logging

import logcat.logcat

actual typealias LogPriority = logcat.LogPriority

actual inline fun Any.logcat(
    priority: LogPriority,
    tag: String?,
    message: () -> String
) {
    logcat(priority, tag, message)
}