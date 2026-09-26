package com.simplecityapps.shuttle.debug.livelog

import kotlinx.coroutines.flow.StateFlow

/** A bounded, in-memory record of [com.simplecityapps.shuttle.debug.DebugLoggingTree]'s recent output. */
interface LiveLogBuffer {
    /** Oldest first. */
    val lines: StateFlow<List<LiveLogLine>>

    fun clear()
}
