package com.simplecityapps.shuttle.settings

import javax.inject.Inject

/**
 * Settles Dynamic colour once, on launch, when nothing is stored for it: on for a new install (#496), off for
 * a user who already picked an accent, so their colour stays. A stored choice is never changed, and a later
 * accent pick doesn't turn dynamic colour off.
 */
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
