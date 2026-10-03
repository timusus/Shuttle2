package com.simplecityapps.shuttle.shared.telemetry

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/** One formatted Kotlin exception: what the Swift crash reporter needs, as plain strings. */
data class KotlinCrashReport(
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
)

/**
 * An uncaught Kotlin exception, as text. Sentry on iOS walks the Objective-C frame chain, which knows nothing about a
 * Kotlin exception, so the class, message and Kotlin stack are handed over for Swift to attach to its event
 * (`KotlinCrashEvent`). The cause chain follows, `Caused by:` per link, capped rather than looped. As Shuttle Podcasts
 * does (`CrashHooks.ios.kt`).
 */
@OptIn(ExperimentalNativeApi::class)
fun formatKotlinCrash(throwable: Throwable): KotlinCrashReport {
    val exceptionClass = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Throwable"
    val stackTrace = buildString {
        appendStackTrace(throwable)
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSES) {
            append("\nCaused by: ")
            append(cause::class.qualifiedName ?: cause::class.simpleName ?: "Throwable")
            cause.message?.let { append(": ").append(it) }
            appendStackTrace(cause, indent = "    ")
            cause = cause.cause
            depth++
        }
    }
    return KotlinCrashReport(exceptionClass = exceptionClass, message = throwable.message.orEmpty(), stackTrace = stackTrace)
}

private const val MAX_CAUSES = 20

// Each element is a Native backtrace line, `<index> <module> <hex address> kfun:<symbol> + <offset>`, with a
// `(<file>:<line>:<col>)` location in binaries that keep debug info: what Swift's parser expects, joined verbatim
@OptIn(ExperimentalNativeApi::class)
private fun StringBuilder.appendStackTrace(
    throwable: Throwable,
    indent: String = ""
) {
    throwable.getStackTrace().forEach { element -> append('\n').append(indent).append(element) }
}

/**
 * Routes the runtime's uncaught exceptions to [reporter], then terminates as the runtime would have. [reporter] may
 * run on any thread and must return quickly. If formatting or [reporter] throws, the process still terminates on the
 * original exception: the crash is never swallowed by its own reporter. From Swift,
 * `CrashHooksKt.installUnhandledExceptionHook(reporter:)`.
 */
@OptIn(ExperimentalNativeApi::class)
fun installUnhandledExceptionHook(reporter: (message: String, stackTrace: String, exceptionClass: String) -> Unit) {
    setUnhandledExceptionHook { throwable ->
        try {
            val report = formatKotlinCrash(throwable)
            reporter(report.message, report.stackTrace, report.exceptionClass)
        } finally {
            terminateWithUnhandledException(throwable)
        }
    }
}
