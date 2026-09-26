package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The stored value of [Setting]s in the [SettingsStore]: the current value, then every change to it. */
class ObserveSetting @Inject constructor(
    private val settingsStore: SettingsStore
) {
    operator fun <T> invoke(setting: Setting<T>): Flow<T> = settingsStore.preference(setting).flow
}

/** Reads a [Setting]'s stored value, or its default when nothing is stored. */
class ReadSetting @Inject constructor(
    private val settingsStore: SettingsStore
) {
    operator fun <T> invoke(setting: Setting<T>): T = settingsStore.preference(setting).value
}

/** Stores a [Setting]'s value. */
class SaveSetting @Inject constructor(
    private val settingsStore: SettingsStore
) {
    operator fun <T> invoke(
        setting: Setting<T>,
        value: T
    ) {
        settingsStore.preference(setting).value = value
    }
}
