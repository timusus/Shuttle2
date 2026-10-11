package com.simplecityapps.shuttle.entitlement

/** How a [StartTrial] ended. */
enum class TrialStart {
    Started,

    /** The user already has Pro or has had the trial. */
    NotEligible,

    /** Play didn't say whether the user owns Pro in time, so the trial was left unstarted rather than risk a purchaser's. */
    Unavailable
}

/** Starts the free trial on the user's say-so (the paywall's and the first-run offer's "Start free trial"). */
fun interface StartTrial {
    suspend operator fun invoke(): TrialStart
}
