package com.simplecityapps.shuttle.entitlement

/**
 * Starts the free trial on the user's say-so (the paywall's and the first-run offer's "Start free trial"). True if it
 * started; false if the user already has Pro or has had the trial.
 */
fun interface StartTrial {
    suspend operator fun invoke(): Boolean
}
