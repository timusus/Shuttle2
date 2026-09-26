package com.simplecityapps.shuttle.debug.livelog

/**
 * Where [com.simplecityapps.shuttle.debug.DebugLoggingTree] forwards each log line for the debug-only Live log
 * screen. Bound with `@BindsOptionalOf`: only the debug build ships an implementation (the in-memory buffer,
 * `android/app/src/debug`), so release never carries the buffer and this call is a no-op.
 */
interface LiveLogSink {
    fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    )
}
