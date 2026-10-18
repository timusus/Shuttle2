package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.Inject

/** Settles Dynamic colour once on launch: on for a new install, off for anyone who already picked an accent. */
class SettleDynamicColourDefault @Inject constructor(
    private val appearanceSettings: AppearanceSettings
) {
    operator fun invoke() {
        val dynamicColour = appearanceSettings.dynamicColour
        if (!dynamicColour.isSet()) {
            dynamicColour.value = !appearanceSettings.accent.isSet()
        }
    }
}
