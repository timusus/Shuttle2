package com.simplecityapps.shuttle.downloads

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

/**
 * Runs [block] on the main thread and blocks the caller until it completes, returning its result.
 * Runs [block] directly, with no hop, when already called from main.
 */
internal fun <T> runOnMainThreadBlocking(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return block()
    }
    var result: T? = null
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        result = block()
        latch.countDown()
    }
    latch.await()
    @Suppress("UNCHECKED_CAST")
    return result as T
}
