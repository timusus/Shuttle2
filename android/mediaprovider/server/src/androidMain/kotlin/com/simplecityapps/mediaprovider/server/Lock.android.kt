package com.simplecityapps.mediaprovider.server

internal actual class Lock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = synchronized(this) { block() }
}
