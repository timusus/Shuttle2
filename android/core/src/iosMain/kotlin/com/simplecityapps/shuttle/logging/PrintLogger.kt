package com.simplecityapps.shuttle.logging

internal actual fun platformLogger(tag: String): Logger = PrintLogger(tag)

/** The log until the iOS app installs its own (`:shared`'s `installNativeLogging`), and in tests: stdout. */
private class PrintLogger(
    private val tag: String
) : Logger {
    override fun debug(
        throwable: Throwable?,
        message: () -> String
    ) = print("D", throwable, message)

    override fun info(
        throwable: Throwable?,
        message: () -> String
    ) = print("I", throwable, message)

    override fun warn(
        throwable: Throwable?,
        message: () -> String
    ) = print("W", throwable, message)

    override fun error(
        throwable: Throwable?,
        message: () -> String
    ) = print("E", throwable, message)

    private fun print(
        level: String,
        throwable: Throwable?,
        message: () -> String
    ) = println("$level/$tag: ${message()}${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
}
