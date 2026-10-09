package com.simplecityapps.shuttle.ui.actions

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
class ConfirmationHandoff<T>(
    private val pickupTimeout: Duration = 5.seconds,
    private val answerTimeout: Duration = 5.minutes,
) {
    class Request<T> internal constructor(val payload: T) {
        /** Names this request to [deliver] and [abandon]; random, so a token saved before process death matches nothing. */
        val token: Long = Random.nextLong()
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
     * @return true if the user accepted; false if they declined, no host picked the request up within the pickup
     * timeout, or no answer came back within the answer timeout. A cancelled caller withdraws its request, so no host
     * launches a dialog nobody waits for.
     */
    suspend fun confirm(payload: T): Boolean = mutex.withLock {
        val request = Request(payload)
        current.value = request
        try {
            withTimeoutOrNull(pickupTimeout) { request.pickedUp.await() } ?: return@withLock false
            // An answer can be lost with its host, so later callers wait at most this long behind it
            withTimeoutOrNull(answerTimeout) { request.answer.await() } ?: false
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

    /** Completes the launched request named by [token] with the user's answer; does nothing if it's no longer waiting. */
    fun deliver(token: Long, accepted: Boolean) {
        current.value?.takeIf { it.token == token && it.pickedUp.isCompleted }?.answer?.complete(accepted)
    }

    /** Declines the request named by [token] because its host is going away for good and its answer can't come back. */
    fun abandon(token: Long) = deliver(token, false)
}
