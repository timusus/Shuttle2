package com.simplecityapps.shuttle.shared.logging

import com.simplecityapps.shuttle.logging.Logger
import kotlin.concurrent.Volatile

enum class NativeLogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Where Kotlin log lines go on iOS: Swift's `os.Logger`, handed over at launch ([installNativeLogging]). Swift owns the
 * call so its format string is a literal in the binary's `__oslogstring` section; a format string passed from Kotlin
 * data isn't there, and `log show` renders such entries as `<compose failure>`.
 */
interface NativeLogSink {
    fun log(
        level: NativeLogLevel,
        category: String,
        message: String
    )
}

@Volatile
private var sink: NativeLogSink? = null

/** Routes every Kotlin [Logger] to [sink], with the tag as the os_log category. Call once, before the graph is built. */
fun installNativeLogging(sink: NativeLogSink) {
    com.simplecityapps.shuttle.shared.logging.sink = sink
    Logger.install { tag -> OsLogLogger(tag) }
}

/** Logs to the unified log under the app's subsystem, with [tag] as the category (until a sink is installed: stdout). */
class OsLogLogger(
    private val tag: String
) : Logger {
    override fun debug(
        throwable: Throwable?,
        message: () -> String
    ) = log(NativeLogLevel.DEBUG, throwable, message)

    override fun info(
        throwable: Throwable?,
        message: () -> String
    ) = log(NativeLogLevel.INFO, throwable, message)

    override fun warn(
        throwable: Throwable?,
        message: () -> String
    ) = log(NativeLogLevel.WARN, throwable, message)

    override fun error(
        throwable: Throwable?,
        message: () -> String
    ) = log(NativeLogLevel.ERROR, throwable, message)

    private fun log(
        level: NativeLogLevel,
        throwable: Throwable?,
        message: () -> String
    ) {
        val text = throwable?.let { "${message()}\n${it.stackTraceToString()}" } ?: message()
        sink?.log(level, tag, text) ?: println("[$tag] $text")
    }
}
