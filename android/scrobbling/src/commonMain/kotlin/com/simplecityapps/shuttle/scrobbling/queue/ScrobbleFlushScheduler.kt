package com.simplecityapps.shuttle.scrobbling.queue

/**
 * Runs [com.simplecityapps.shuttle.scrobbling.flush.ScrobbleFlusher] at some point once the device is online: a unique
 * WorkManager job on Android (WorkManagerScrobbleFlushScheduler), in-process on iOS
 * ([com.simplecityapps.shuttle.scrobbling.flush.InProcessScrobbleFlushScheduler]).
 */
interface ScrobbleFlushScheduler {
    fun scheduleFlush()
}
