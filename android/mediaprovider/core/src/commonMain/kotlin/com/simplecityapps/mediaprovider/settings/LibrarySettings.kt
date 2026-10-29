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
    val mergeCopies = store.preference(MergeCopies)
    val preferredCopy = store.preference(PreferredCopy)
    val primaryCopies = store.preference(PrimaryCopies)
    val primaryAlbums = store.preference(PrimaryAlbums)

    /** One-time move off the old default of never, run at launch before the background sync is scheduled. */
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

        /** Show a song held by several sources once, its best copy standing for the rest. */
        val MergeCopies = Setting.boolean("pref_merge_copies", true)

        /** Stored by name. */
        val PreferredCopy = Setting.string(
            key = "pref_preferred_copy",
            default = com.simplecityapps.shuttle.model.PreferredCopy.Local,
            decode = { value -> com.simplecityapps.shuttle.model.PreferredCopy.entries.firstOrNull { it.name == value } },
            encode = { preferred -> preferred.name }
        )

        /** [com.simplecityapps.shuttle.model.copyKey]s of the copies made primary, one per line. */
        val PrimaryCopies = keySet("pref_primary_copies")

        /** [com.simplecityapps.shuttle.model.copyAlbumKey]s of the albums made primary from one source, one per line. */
        val PrimaryAlbums = keySet("pref_primary_albums")

        // A newline never appears in a source's name, a server id or a path's tags.
        private fun keySet(key: String) = Setting.string<Set<String>>(
            key = key,
            default = emptySet(),
            decode = { value -> value.split('\n').filter { it.isNotEmpty() }.toSet() },
            encode = { keys -> keys.joinToString("\n") }
        )
    }
}
