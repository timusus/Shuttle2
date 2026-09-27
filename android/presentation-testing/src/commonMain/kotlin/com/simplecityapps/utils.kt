package com.simplecityapps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun <T> neverEmittingFlow(): Flow<T> = MutableSharedFlow()

/** A list that tests can append to from the repository's worker threads as well as the test's own. */
expect fun <T> threadSafeList(): MutableList<T>
