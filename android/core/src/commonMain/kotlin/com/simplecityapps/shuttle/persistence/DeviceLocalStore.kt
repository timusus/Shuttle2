package com.simplecityapps.shuttle.persistence

import com.simplecityapps.shuttle.settings.Preference
import com.simplecityapps.shuttle.settings.Setting

/** Values that describe this device (a permission already asked for) and so must not be restored from a backup. */
class DeviceLocalStore(
    internal val store: KeyValueStore
) {
    fun <T> preference(setting: Setting<T>): Preference<T> = Preference(store, setting)
}
