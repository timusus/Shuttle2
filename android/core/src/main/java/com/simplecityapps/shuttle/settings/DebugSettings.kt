package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Settings > About > Advanced. */
@SingleIn(AppScope::class)
class DebugSettings @Inject constructor(
    store: SettingsStore
) {
    val fileLogging = store.preference(FileLogging)

    companion object {
        val FileLogging = Setting.boolean("pref_file_logging", false)
    }
}
