package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

/** Settings > About > Advanced. */
@Singleton
class DebugSettings @Inject constructor(
    store: SettingsStore
) {
    val fileLogging = store.preference(FileLogging)

    companion object {
        val FileLogging = Setting.boolean("pref_file_logging", false)
    }
}
