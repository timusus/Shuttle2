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
    val copyRecordingIds = store.preference(CopyRecordingIds)

    companion object {
        val FileLogging = Setting.boolean("pref_file_logging", false)

        /** Whether song copies merge on a shared MusicBrainz recording id; off checks the plain-tag rule alone. Debug builds set it. */
        val CopyRecordingIds = Setting.boolean("pref_copy_recording_ids", true)
    }
}
