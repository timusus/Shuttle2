package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.ui.common.PendingEvents
import kotlin.coroutines.cancellation.CancellationException

/** Runs a primary copy or source change; a failure is logged and posted as a message rather than reaching the uncaught-exception handler. */
suspend fun PendingEvents<MediaActionMessage>.postSourceChoiceFailure(logger: Logger, change: suspend () -> Unit) {
    try {
        change()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error(e) { "Changing the primary source failed" }
        post(MediaActionMessage.SourceChoiceFailed)
    }
}
