package com.simplecityapps.shuttle.logging

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import platform.darwin.OS_LOG_TYPE_DEBUG
import platform.darwin.OS_LOG_TYPE_DEFAULT
import platform.darwin.OS_LOG_TYPE_ERROR
import platform.darwin.OS_LOG_TYPE_INFO
import platform.darwin.__dso_handle
import platform.darwin._os_log_internal
import platform.darwin.os_log_create
import platform.darwin.os_log_type_t

internal actual fun platformLogger(tag: String): Logger = OsLogLogger(tag)

/**
 * Logs to the unified log (Console.app, `log stream`) under the app's subsystem, with [tag] as the category. Warnings
 * go out at the default level, which os_log has no separate warning level below.
 */
@OptIn(ExperimentalForeignApi::class)
class OsLogLogger(
    tag: String
) : Logger {
    private val log = os_log_create(SUBSYSTEM, tag)

    override fun debug(
        throwable: Throwable?,
        message: () -> String
    ) = log(OS_LOG_TYPE_DEBUG, throwable, message)

    override fun info(
        throwable: Throwable?,
        message: () -> String
    ) = log(OS_LOG_TYPE_INFO, throwable, message)

    override fun warn(
        throwable: Throwable?,
        message: () -> String
    ) = log(OS_LOG_TYPE_DEFAULT, throwable, message)

    override fun error(
        throwable: Throwable?,
        message: () -> String
    ) = log(OS_LOG_TYPE_ERROR, throwable, message)

    private fun log(
        type: os_log_type_t,
        throwable: Throwable?,
        message: () -> String
    ) {
        val text = throwable?.let { "${message()}\n${it.stackTraceToString()}" } ?: message()
        // "%{public}s" so the message isn't redacted as <private> outside the debugger
        _os_log_internal(__dso_handle.ptr, log, type, "%{public}s", text)
    }

    private companion object {
        const val SUBSYSTEM = "com.simplecityapps.shuttle2"
    }
}
