package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.networking.NetworkConnectivity
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleFlushScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [ScrobbleFlusher] in the app's process, for a platform without WorkManager (iOS binds it in `:shared`). A
 * flush is never run twice at once: every run holds [running], and a request that arrives while one is waiting to
 * start joins it rather than queuing another. Offline requests are dropped; the next enqueue, launch or foreground
 * asks again. A [FlushResult.Retry] schedules one more attempt after an exponential backoff
 * ([INITIAL_BACKOFF] doubling to [MAX_BACKOFF]), cancelled by the next run that starts. If the device is offline
 * when that backoff ends, the retry keeps checking every [OFFLINE_POLL] until it is connected, rather than dropping.
 */
@SingleIn(AppScope::class)
class InProcessScrobbleFlushScheduler
@Inject
constructor(
    private val flusher: ScrobbleFlusher,
    private val connectivity: NetworkConnectivity,
    @AppCoroutineScope private val scope: CoroutineScope
) : ScrobbleFlushScheduler {
    private val running = Mutex()
    private val pending = AtomicBoolean(false)
    private var retryJob: Job? = null
    private var backoff = INITIAL_BACKOFF

    override fun scheduleFlush() {
        if (!connectivity.isConnected()) return
        // One run waiting to start covers every request made before it starts.
        if (!pending.compareAndSet(expectedValue = false, newValue = true)) return
        scope.launch {
            running.withLock {
                pending.store(false)
                run()
            }
        }
    }

    /** Runs a flush now, waiting for one already running to finish first; for a background task that must await it. */
    suspend fun flush(): FlushResult = running.withLock { run() }

    // Called holding [running].
    private suspend fun run(): FlushResult {
        retryJob?.cancel()
        retryJob = null
        val result = flusher.flush()
        if (result == FlushResult.Retry) {
            val wait = backoff
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
            retryJob = scope.launch {
                delay(wait)
                while (!connectivity.isConnected()) delay(OFFLINE_POLL)
                scheduleFlush()
            }
        } else {
            backoff = INITIAL_BACKOFF
        }
        return result
    }

    companion object {
        val INITIAL_BACKOFF: Duration = 30.seconds
        val MAX_BACKOFF: Duration = 30.minutes
        val OFFLINE_POLL: Duration = 30.seconds
    }
}
