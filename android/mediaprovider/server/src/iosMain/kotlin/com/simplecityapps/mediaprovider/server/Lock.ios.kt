package com.simplecityapps.mediaprovider.server

import platform.Foundation.NSLock

internal actual class Lock actual constructor() {
    private val lock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
