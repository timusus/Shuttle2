package com.simplecityapps.shuttle.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether streaming from a remote server currently needs S2 Pro: true for a Free user who hasn't had, or has
 * used up, the server trial.
 */
fun interface ObserveServerStreamingNeedsPro {
    operator fun invoke(): StateFlow<Boolean>
}
