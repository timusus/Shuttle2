package com.simplecityapps.shuttle.shared.settings

import com.simplecityapps.shuttle.settings.AppearanceSettings
import platform.Foundation.NSUserDefaults

/** Registered in NSUserDefaults' registration domain, so a value the user sets still wins over these defaults. */
object IosSettingDefaults {
    val values: Map<String, Any> = mapOf(
        AppearanceSettings.ShowHomeOnLaunch.key to true
    )

    /** Before the first read: the graph's `KeyValueStore` registers them when it's created. */
    fun register() {
        NSUserDefaults.standardUserDefaults.registerDefaults(values.mapKeys { it.key as Any? })
    }
}
