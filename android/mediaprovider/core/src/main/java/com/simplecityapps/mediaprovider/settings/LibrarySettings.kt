package com.simplecityapps.mediaprovider.settings

import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Settings > Library (scanning) and Settings > Sources (servers). */
@Singleton
class LibrarySettings @Inject constructor(
    store: SettingsStore
) {
    val rescanFrequency = store.preference(RescanFrequency)
    val reportPlaybackToServer = store.preference(ReportPlaybackToServer)

    companion object {
        /** Stored as [ImportFrequency.value] in a string, as the ListPreference wrote it. */
        val RescanFrequency = Setting.string(
            key = "pref_media_rescan_frequency",
            default = ImportFrequency.Never,
            decode = { value -> ImportFrequency.entries.firstOrNull { it.value.toString() == value } },
            encode = { frequency -> frequency.value.toString() }
        )

        /** Report what's playing to Jellyfin, Emby and Plex, all three. */
        val ReportPlaybackToServer = Setting.boolean("pref_report_playback", true)
    }
}
