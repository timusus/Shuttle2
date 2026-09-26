package com.simplecityapps.playback

import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Waits for this coroutine's result on a thread an API hands us to block on and expects an answer from, such as a
 * Media3 loader thread or a NanoHTTPD request thread. Never call it on the main thread.
 *
 * Unlike `runBlocking`, the waiting thread runs none of the work: the coroutine runs in its owner's scope, which
 * bounds its lifetime, and others can wait for the same result. An interrupt (how Media3 cancels a load) ends the
 * wait at once with an [InterruptedIOException], leaving the work to finish for anyone else waiting on it.
 *
 * @throws TimeoutException if it's still running after [timeout].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Throws(InterruptedIOException::class, TimeoutException::class)
internal fun <T> Deferred<T>.awaitBlocking(timeout: Duration = Duration.INFINITE): T {
    if (!isCompleted) {
        val completed = CountDownLatch(1)
        val handle = invokeOnCompletion { completed.countDown() }
        try {
            if (!completed.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                throw TimeoutException("Still running after $timeout")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while waiting").apply { initCause(e) }
        } finally {
            handle.dispose()
        }
    }
    return getCompleted()
}
