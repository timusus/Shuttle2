package com.simplecityapps.playback.mediasession

import androidx.media3.session.MediaSession.ControllerInfo
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

/**
 * Whether a car may use the library: Android Auto is part of Shuttle Music Pro. A car's first use starts the shared
 * trial; once the trial has ended without Pro, a car gets only the upgrade item, and the phone app (never the car)
 * offers the upgrade. A store that hasn't answered yet never locks a purchaser out. Any other controller (the app
 * itself, the notification, a Bluetooth headset) is never gated.
 */
class CarAccess(
    private val gate: ServerAccessGate,
    private val carPackages: Set<String> = CAR_PACKAGES
) {
    /** Whether [controller] is a car's media browser (Android Auto on the phone, Android Automotive, or the desktop head unit). */
    fun isCar(controller: ControllerInfo): Boolean = controller.packageName in carPackages

    /** Whether [controller] may browse, search and play the library: anything but a car, or a car while Android Auto is unlocked. */
    suspend fun mayUseLibrary(controller: ControllerInfo): Boolean = !isCar(controller) || gate.tryUse(ProFeature.AndroidAuto, askForPaywall = false)

    /** Each time Android Auto locks or unlocks (a purchase, or the trial ending), for a connected car to refresh its root. */
    val lockChanges: Flow<Boolean> = gate.locked.drop(1)

    companion object {
        /** Android Auto's, Android Automotive's and the desktop head unit's (Android Auto's simulator) media browsers. */
        val CAR_PACKAGES = setOf("com.google.android.projection.gearhead", "com.android.car.media", "com.google.android.autosimulator")
    }
}
