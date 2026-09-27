package com.simplecityapps.shuttle.settings

import com.simplecityapps.shuttle.persistence.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Binds [Setting]s to the app's default [KeyValueStore], the file the settings screens have always written. */
@SingleIn(AppScope::class)
class SettingsStore @Inject constructor(
    private val store: KeyValueStore
) {
    fun <T> preference(setting: Setting<T>): Preference<T> = Preference(store, setting)
}
