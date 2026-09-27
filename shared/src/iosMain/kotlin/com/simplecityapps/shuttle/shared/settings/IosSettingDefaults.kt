package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.shuttle.settings.AppearanceSettings
import platform.Foundation.NSUserDefaults

/**
 * Where iOS's default for a shared setting departs from Android's. Registered in NSUserDefaults' registration
 * domain, which every suite in the process reads and nothing saves, so a value the user sets still wins.
 *
 * - Show Home on launch: iOS opens on Home, as Music does (#617, #623); Android keeps opening on Library.
 */
object IosSettingDefaults {
    val values: Map<String, Any> = mapOf(
        AppearanceSettings.ShowHomeOnLaunch.key to true
    )

    /** Before the first read: the graph's `KeyValueStore` registers them when it's created. */
    fun register() {
        NSUserDefaults.standardUserDefaults.registerDefaults(values.mapKeys { it.key as Any? })
    }
}
