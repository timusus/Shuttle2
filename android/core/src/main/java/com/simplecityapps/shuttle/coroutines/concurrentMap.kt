package com.simplecityapps.shuttle.coroutines

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow

@OptIn(FlowPreview::class)
fun <T, R> Flow<T>.concurrentMap(
    concurrencyLevel: Int,
    transform: suspend (T) -> R
): Flow<R> = flatMapMerge(concurrencyLevel) { value ->
    flow { emit(transform(value)) }
}
