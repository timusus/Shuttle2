package com.simplecityapps.localmediaprovider.local.provider

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** A file this big takes every permit: TagLib parses its embedded art into native memory, which a few at once can exhaust. */
const val LARGE_TAG_READ_BYTES = 30L * 1024 * 1024

/**
 * Caps the native tag reads running at once, across every flow that reads them (#840): each read holds one of [permits],
 * and a read that must run alone (a large file, a suspect) holds them all.
 */
class TagReadLimiter(val permits: Int = defaultTagReadPermits()) {
    private val semaphore = Semaphore(permits)

    // Held while taking every permit, so two reads that want them all can't each hold some waiting on the other
    private val exclusive = Mutex()

    suspend fun <T> withPermits(
        all: Boolean,
        block: suspend () -> T
    ): T {
        if (!all) return semaphore.withPermit { block() }
        return exclusive.withLock {
            var held = 0
            try {
                repeat(permits) {
                    semaphore.acquire()
                    held++
                }
                block()
            } finally {
                repeat(held) { semaphore.release() }
            }
        }
    }
}

/** One fewer than the cores, leaving one for the UI, and at most four, which keeps the native memory art takes bounded. */
fun defaultTagReadPermits(): Int = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
