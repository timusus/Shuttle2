package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Whether CarPlay may browse and play the library: CarPlay is part of Shuttle Music Pro, as Android Auto is
 * (Android's `CarAccess`). The car can't show the paywall, and on iOS only the paywall starts the trial, so while Pro
 * is locked ([ServerAccessGate.locked]: free, whether or not the trial was had) CarPlay shows only an upgrade message
 * pointing at the phone. A store that hasn't answered yet leaves it unlocked, so a purchaser is never locked out, and a
 * purchase, a trial started on the phone or the trial ending changes it while the car is connected.
 */
class CarPlayAccess(
    gate: ServerAccessGate,
    coroutineScope: CoroutineScope
) {
    val locked: StateFlow<Boolean> = gate.locked.stateIn(coroutineScope, SharingStarted.Eagerly, gate.isLocked)
}
