package com.simplecityapps.mediaprovider.server

/** A lock for short, non-suspending critical sections, which common Kotlin has no `synchronized` for. */
internal expect class Lock() {
    fun <T> withLock(block: () -> T): T
}
