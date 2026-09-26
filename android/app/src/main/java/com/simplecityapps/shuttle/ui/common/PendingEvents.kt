package com.simplecityapps.shuttle.ui.common

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An event waiting in a UiState for the UI to handle it; [id] is what the UI hands back to consume it. */
data class PendingEvent<out T>(
    val id: Long,
    val value: T
)

/**
 * A ViewModel's events whose loss would be a bug, held as state until the UI consumes them (UDF doc principle 4).
 * [post] appends, the ViewModel combines [flow] into its UiState, and the UI's consume action calls [consume].
 */
class PendingEvents<T> {
    private val nextId = AtomicLong()
    private val events = MutableStateFlow<List<PendingEvent<T>>>(emptyList())

    val flow: StateFlow<List<PendingEvent<T>>> = events.asStateFlow()

    fun post(event: T) {
        val pending = PendingEvent(nextId.incrementAndGet(), event)
        events.update { it + pending }
    }

    fun consume(id: Long) {
        events.update { pending -> pending.filterNot { it.id == id } }
    }
}
