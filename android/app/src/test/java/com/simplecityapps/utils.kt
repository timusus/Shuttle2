package com.simplecityapps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun <T> neverEmittingFlow(): Flow<T> = MutableSharedFlow()
