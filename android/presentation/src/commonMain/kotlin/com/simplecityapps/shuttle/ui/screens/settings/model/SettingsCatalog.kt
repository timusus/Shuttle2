package com.simplecityapps.shuttle.ui.screens.settings.model

import com.simplecityapps.shuttle.settings.Setting

/** The settings screens a platform shows, as plain data its UI renders. */
interface SettingsCatalog {
    val screens: List<SettingsScreen>

    fun screen(destination: SettingsDestination): SettingsScreen = screens.first { it.destination == destination }

    val items: List<SettingItem> get() = screens.flatMap { it.items }

    /** Every setting a catalog row stores or depends on. */
    val settings: List<Setting<*>>
        get() = items
            .flatMap { item ->
                val stored = when (item) {
                    is SettingItem.Switch -> item.setting
                    is SettingItem.Choice<*> -> item.setting
                    is SettingItem.Slider<*> -> item.setting
                    is SettingItem.Navigate, is SettingItem.Action -> null
                }
                listOfNotNull(stored, item.dependsOn, (item as? SettingItem.Choice<*>)?.overriddenBy?.setting)
            }
            .distinctBy { it.key }
}
