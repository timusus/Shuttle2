package com.simplecityapps.mediaprovider.settings

import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.shuttle.model.MinTrackLength
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Settings > Library (scanning) and Settings > Sources (servers). */
@SingleIn(AppScope::class)
class LibrarySettings @Inject constructor(
    store: SettingsStore
) {
    val rescanFrequency = store.preference(RescanFrequency)
    val rescanFrequencyMigrated = store.preference(RescanFrequencyMigrated)
    val reportPlaybackToServer = store.preference(ReportPlaybackToServer)
    val minTrackLength = store.preference(MinTrackLength)

    /**
     * Moves anyone who chose never, when it was the default, to the daily sync (#771): once, so never chosen after this
     * stays. Asked at launch, before the background sync is scheduled.
     */
    fun migrateRescanFrequency() {
        if (rescanFrequencyMigrated.value) return
        if (rescanFrequency.value == ImportFrequency.Never) rescanFrequency.value = ImportFrequency.Daily
        rescanFrequencyMigrated.value = true
    }

    companion object {
        /** Stored as [ImportFrequency.value] in a string, as the ListPreference wrote it. */
        val RescanFrequency = Setting.string(
            key = "pref_media_rescan_frequency",
            default = ImportFrequency.Daily,
            decode = { value -> ImportFrequency.entries.firstOrNull { it.value.toString() == value } },
            encode = { frequency -> frequency.value.toString() }
        )

        /** Whether [migrateRescanFrequency] has run. */
        val RescanFrequencyMigrated = Setting.boolean("pref_media_rescan_frequency_migrated", false)

        /** Stored by name, so lengths can be added anywhere in the list. */
        val MinTrackLength = Setting.string(
            key = "pref_min_track_length",
            default = com.simplecityapps.shuttle.model.MinTrackLength.Off,
            decode = { value -> com.simplecityapps.shuttle.model.MinTrackLength.entries.firstOrNull { it.name == value } },
            encode = { length -> length.name }
        )

        /** Report what's playing to Jellyfin, Emby and Plex, all three. */
        val ReportPlaybackToServer = Setting.boolean("pref_report_playback", true)
    }
}
