package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.shuttle.ui.screens.settings.SettingsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.text.StringKey
import com.simplecityapps.shuttle.ui.text.resolve
import com.simplecityapps.shuttle.ui.text.text

// Reads Swift's SettingsView makes of the shared settings state. `SettingsUiState.value` is generic over the
// setting's type, which Objective-C erases, so these answer in the plain types each row draws.

/** Whether [item]'s switch is on. */
fun SettingsUiState.isOn(item: SettingItem.Switch): Boolean = value(item.setting)

/** The index into [item]'s options of the stored value, or -1 if it matches none. */
fun SettingsUiState.selectedIndex(item: SettingItem.Choice<*>): Int {
    val current = value(item.setting)
    return item.options.indexOfFirst { it.value == current }
}

/**
 * False while the switch [item] depends on is off, or a switch that takes it over is on and shown in [catalog]
 * (Android's rule: a hidden switch takes nothing over).
 */
fun SettingsUiState.isEnabled(
    item: SettingItem,
    catalog: SettingsCatalog
): Boolean {
    val dependency = item.dependsOn?.let { value(it) } ?: true
    val override = (item as? SettingItem.Choice<*>)?.overriddenBy
        ?.takeIf { override -> catalog.items.any { it is SettingItem.Switch && it.key == override.setting.key } }
    return dependency && !(override?.let { value(it.setting) } ?: false)
}

/** [this] string from the app's Localizable table. */
fun StringKey.localized(): String = text.resolve()
