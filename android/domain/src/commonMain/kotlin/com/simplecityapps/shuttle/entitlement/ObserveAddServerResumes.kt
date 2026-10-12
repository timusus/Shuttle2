package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.SharedFlow

/**
 * Emits the type a refused add-server was for when the paywall it opened closes with the add now allowed (the trial
 * started or Pro was bought there), so the add-server flow carries on with that type. Dropped while nothing collects it.
 */
fun interface ObserveAddServerResumes {
    operator fun invoke(): SharedFlow<MediaProviderType>
}
