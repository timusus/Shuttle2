package com.simplecityapps.shuttle.logging

expect enum class LogPriority {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT
}

// Called logcat, rather than a more generic name, because it's relatively unique
expect inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    /**
     * If provided, the log will use this tag instead of the simple class name of `this` at the call
     * site.
     */
    tag: String? = null,
    message: () -> String
)