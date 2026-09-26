package com.simplecityapps.shuttle.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Handles the oldest of [events], then reports it consumed, one at a time. An event whose handling is interrupted
 * (the screen leaves composition first) stays pending and is handled again when the screen comes back.
 */
@Composable
fun <T> ConsumeEvents(
    events: List<PendingEvent<T>>,
    onConsumed: (Long) -> Unit,
    handler: suspend (T) -> Unit
) {
    val currentHandler by rememberUpdatedState(handler)
    val currentOnConsumed by rememberUpdatedState(onConsumed)
    val event = events.firstOrNull() ?: return
    LaunchedEffect(event.id) {
        currentHandler(event.value)
        currentOnConsumed(event.id)
    }
}
