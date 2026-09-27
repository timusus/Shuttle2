package com.simplecityapps.shuttle.shared.di

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [Flow] Swift feeds: state only Swift has (preferences in `UserDefaults`, the scene phase, the audio route) handed
 * to shared code. Copied from Shuttle Podcasts' `WritableFlow.kt`.
 *
 * Swift can't implement a Kotlin `Flow` under SKIE (its suspend `collect` becomes an async throwing method, which no
 * longer matches the protocol), so it creates one of these and pushes values with [emit]; `toKotlinFlow` in
 * ios/S2/KMP/CombineFlowBridge.swift does that from a Combine publisher. Replays the latest value to each new
 * collector, as a `StateFlow` would, but doesn't deduplicate: the Swift feed does.
 */
class WritableFlow<T> : Flow<T> {
    private val delegate = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Keeps the Swift side's subscription (a Combine `AnyCancellable`) alive for as long as this flow. */
    var subscription: Any? = null

    override suspend fun collect(collector: FlowCollector<T>) {
        delegate.collect(collector)
    }

    /** Pushes [value] to every collector, and to each later one. Never suspends; callable from any thread. */
    fun emit(value: T) {
        delegate.tryEmit(value)
    }
}
