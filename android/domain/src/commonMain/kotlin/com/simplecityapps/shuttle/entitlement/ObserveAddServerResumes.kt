package com.simplecityapps.shuttle.entitlement

import kotlinx.coroutines.flow.SharedFlow

/**
 * Emits when the paywall a refused add-server opened closes with the add now allowed (the trial started or Pro was
 * bought there), so the add-server flow carries on with the type the user chose. Dropped while nothing collects it.
 */
fun interface ObserveAddServerResumes {
    operator fun invoke(): SharedFlow<Unit>
}
