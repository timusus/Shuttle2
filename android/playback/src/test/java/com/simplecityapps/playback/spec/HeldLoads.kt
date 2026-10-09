package com.simplecityapps.playback.spec

import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.ConditionVariable
import androidx.media3.exoplayer.util.ReleasableExecutor
import com.google.common.base.Supplier
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import org.robolectric.Shadows.shadowOf

/**
 * Where a player's media loads run, given to its media source factory (`MediaSource.Factory.setDownloadExecutor`): a
 * load doesn't start when the player asks for it, but when [runNext] runs it, with the player's thread held, until it
 * has read all it will or waits for the player to want more. So how much of a song is loaded at a given time of the
 * player's clock doesn't depend on how fast the machine runs the load's thread.
 *
 * A progressive load reads a chunk at a time, waiting after each on its `loadCondition` until the player's thread lets
 * it go on, which can happen while the player plays; [runNext] runs a load let go like that to rest again.
 */
class HeldLoads : Supplier<ReleasableExecutor> {
    /** Guarded by this. */
    private val executors = mutableListOf<LoadExecutor>()

    override fun get(): ReleasableExecutor = LoadExecutor().also { synchronized(this) { executors += it } }

    /**
     * Runs one load that is due to start, or that its player has let go on, until it finishes or waits for the player,
     * holding the player's thread meanwhile, then passes that thread's looper to [afterwards] to handle what the load
     * reported to it. Returns false if no load had anything to do.
     */
    fun runNext(afterwards: (Looper) -> Unit): Boolean {
        val load = synchronized(this) { executors.firstNotNullOfOrNull { it.due() } } ?: return false
        val looper = (load.task as? Handler)?.looper?.takeIf { it != Looper.getMainLooper() && it.thread.isAlive }
        looper?.let { shadowOf(it).pause() }
        try {
            if (load.thread.state == Thread.State.NEW) load.thread.start()
            awaitRest(load)
        } finally {
            looper?.let { shadowOf(it).unPause() }
        }
        looper?.let(afterwards)
        return true
    }

    private fun awaitRest(load: Load) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANG_SECONDS)
        while (load.thread.isAlive && !load.isWaitingForPlayer()) {
            if (System.nanoTime() > deadline) {
                throw AssertionError(
                    "${load.thread.name} hung: its load hasn't finished or waited for the player in $HANG_SECONDS s\n" +
                        load.thread.stackTrace.joinToString("\n") { "\tat $it" }
                )
            }
            LockSupport.parkNanos(POLL_NANOS)
        }
    }

    /** One of the executors a player's loaders run their loads on, one at a time, in order. */
    private class LoadExecutor : ReleasableExecutor {
        /** Guarded by this. */
        private val pending = ArrayDeque<Runnable>()

        /** Guarded by this. */
        private var current: Load? = null

        override fun execute(command: Runnable) {
            synchronized(this) { pending += command }
        }

        // What's left still runs: a released loader hands its executor the task that releases its sample queues.
        override fun release() = Unit

        /** The load that has something to do now: the one running, unless it waits for the player, or else the next. */
        @Synchronized
        fun due(): Load? {
            current?.let { load -> if (load.thread.isAlive) return load.takeUnless { it.isWaitingForPlayer() } }
            current = pending.removeFirstOrNull()?.let { Load(it, Thread(it, "Held load")) }
            return current
        }
    }

    private class Load(val task: Runnable, val thread: Thread) {
        private val loadable = (task as? Handler)?.let { loadTaskLoadable.get(it) }

        /**
         * Whether the load waits on its `loadCondition` for the player to let it read on. Not when the condition is
         * open or the load cancelled, both set before the thread is woken, so a thread about to wake doesn't count.
         */
        fun isWaitingForPlayer(): Boolean {
            if (loadable == null || !extractingLoadable.isInstance(loadable)) return false
            val condition = loadCondition.get(loadable) as ConditionVariable
            return thread.state == Thread.State.WAITING && !condition.isOpen && !(loadCanceled.get(loadable) as Boolean)
        }
    }

    private companion object {
        /** How long a load may run without finishing or waiting for the player before it's reported hung. */
        const val HANG_SECONDS = 60L

        val POLL_NANOS = TimeUnit.MICROSECONDS.toNanos(50)

        /** Media3's load internals, which it doesn't expose: a loader task's load, and a progressive load's state. */
        val loadTaskLoadable = Class.forName("androidx.media3.exoplayer.upstream.Loader\$LoadTask").getDeclaredField("loadable").apply { isAccessible = true }
        val extractingLoadable = Class.forName("androidx.media3.exoplayer.source.ProgressiveMediaPeriod\$ExtractingLoadable")
        val loadCondition = extractingLoadable.getDeclaredField("loadCondition").apply { isAccessible = true }
        val loadCanceled = extractingLoadable.getDeclaredField("loadCanceled").apply { isAccessible = true }
    }
}
