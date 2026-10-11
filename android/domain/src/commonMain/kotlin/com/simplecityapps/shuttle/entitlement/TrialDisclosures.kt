package com.simplecityapps.shuttle.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * A trial started by the first use of a Pro feature (only a gate built to start it does; production gates leave it to
 * the user), until the user has been told. It's held rather than sent once, so a trial started where nothing can show
 * it (a car, a song played from the notification) is disclosed the next time the app is on screen.
 */
interface TrialDisclosures {
    /** The feature whose first use started the trial, while that's still to be disclosed; else null. */
    val pending: StateFlow<ProFeature?>

    /** The user has seen the disclosure. */
    fun onDisclosed()
}

/** Keeps [TrialDisclosures.pending] across process death, so a trial started where nothing could show it is still disclosed. */
interface TrialDisclosureStore {
    var pendingDisclosure: ProFeature?
}
