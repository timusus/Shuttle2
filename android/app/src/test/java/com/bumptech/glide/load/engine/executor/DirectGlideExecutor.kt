package com.bumptech.glide.load.engine.executor

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * A [GlideExecutor] that runs each job on the calling thread, so a Robolectric test sees a load
 * finish before it next idles the main looper. Lives in Glide's package because the constructor
 * that takes a delegate is package-private.
 */
fun directGlideExecutor(): GlideExecutor = GlideExecutor(DirectExecutorService())

private class DirectExecutorService : AbstractExecutorService() {
    @Volatile private var shutdown = false

    override fun execute(command: Runnable) = command.run()

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): List<Runnable> {
        shutdown = true
        return emptyList()
    }

    override fun isShutdown(): Boolean = shutdown

    override fun isTerminated(): Boolean = shutdown

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
}
