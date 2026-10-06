package com.simplecityapps.shuttle.ui.actions

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hands system confirmation dialogs ([T] describes one) from a caller to whichever UI host is resumed, and the user's
 * answer back. The request in flight lives here rather than in a composition, so a result delivered to a recreated
 * activity (after a rotation) still completes it. One request is in flight at a time; callers queue behind it.
 */
class ConfirmationHandoff<T>(private val pickupTimeout: Duration = 5.seconds) {
    class Request<T> internal constructor(val payload: T) {
        internal val pickedUp = CompletableDeferred<Unit>()
        internal val answer = CompletableDeferred<Boolean>()
    }

    private val mutex = Mutex()
    private val current = MutableStateFlow<Request<T>?>(null)

    /** Requests no host has launched yet; a host [launch]es each, then [deliver]s the answer. */
    val requests: Flow<Request<T>> = current.filterNotNull().filter { !it.pickedUp.isCompleted }

    /**
     * Waits for a host to launch the dialog for [payload] and for the user to answer it.
     *
     * @return true if the user accepted; false if they declined, or no host picked the request up within the pickup
     * timeout. A cancelled caller withdraws its request, so no host launches a dialog nobody waits for.
     */
    suspend fun confirm(payload: T): Boolean = mutex.withLock {
        val request = Request(payload)
        current.value = request
        try {
            withTimeoutOrNull(pickupTimeout) { request.pickedUp.await() } ?: return@withLock false
            request.answer.await()
        } finally {
            // Once withdrawn, a host can't claim it
            request.pickedUp.cancel()
            current.compareAndSet(request, null)
        }
    }

    /**
     * Claims [request] for the calling host, which then launches its dialog.
     *
     * @return false if another host already launched it or its caller withdrew it.
     */
    fun launch(request: Request<T>): Boolean = current.value === request && request.pickedUp.complete(Unit)

    /** Completes the launched request with the user's answer; does nothing when none is waiting for one. */
    fun deliver(accepted: Boolean) {
        current.value?.takeIf { it.pickedUp.isCompleted }?.answer?.complete(accepted)
    }
}
