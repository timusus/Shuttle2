package com.simplecityapps.shuttle.backup

import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.di.MainDispatcher
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.screens.settings.SettingsEffects
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackup
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * Replaces the preferences with a backup's ([BackedUpSettings]), then runs each changed setting's side effect so it
 * applies without a restart. The preferences are written on [ioDispatcher] and the effects run on [mainDispatcher],
 * since some of them (the night mode, the widgets) must.
 */
class BackedUpSettingsRestorer @Inject constructor(
    private val keyValueStore: KeyValueStore,
    private val effects: SettingsEffects,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) {
    /** Returns the number of preferences written: none from a version 1 backup, which holds no settings and leaves them alone. */
    suspend fun restore(backup: LibraryBackup): Int = backup.settings?.let { restore(it) } ?: 0

    private suspend fun restore(values: Map<String, JsonPrimitive>): Int {
        val (written, changed) = withContext(ioDispatcher) {
            val before = BackedUpSettings.settings.associateWith { it.read(keyValueStore) }
            val written = BackedUpSettings.restore(keyValueStore, values)
            written to BackedUpSettings.settings.filter { setting -> setting.read(keyValueStore) != before[setting] }
        }
        withContext(mainDispatcher) {
            changed.forEach { setting -> applyEffect(setting) }
        }
        return written
    }

    private fun <T> applyEffect(setting: Setting<T>) = effects.onSettingChanged(setting, setting.read(keyValueStore))
}
