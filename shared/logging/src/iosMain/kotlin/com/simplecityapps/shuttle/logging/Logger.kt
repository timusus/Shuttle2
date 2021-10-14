package com.simplecityapps.shuttle.logging


actual enum class LogPriority {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT
}

actual inline fun Any.logcat(
    priority: LogPriority,
    tag: String?,
    message: () -> String
) {

}