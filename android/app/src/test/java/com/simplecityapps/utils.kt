package com.simplecityapps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun <T> neverEmittingFlow(): Flow<T> = flowOf()
