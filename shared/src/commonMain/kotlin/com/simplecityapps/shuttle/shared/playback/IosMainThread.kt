package com.simplecityapps.shuttle.shared.playback

import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The main thread, as [scope]'s dispatcher. */
internal class IosMainThread(private val scope: CoroutineScope) {
    private val dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher

    /** Runs [block] now if on the main thread, else posts it there. */
    fun run(block: () -> Unit) {
        if (dispatcher?.isDispatchNeeded(EmptyCoroutineContext) == true) scope.launch { block() } else block()
    }

    suspend fun <T> call(block: () -> T): T = withContext(dispatcher ?: EmptyCoroutineContext) { block() }
}
