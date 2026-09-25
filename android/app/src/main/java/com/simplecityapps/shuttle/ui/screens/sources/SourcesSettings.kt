package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Where the library comes from on this device: the music permission's history and the scanner's folders. */
@Singleton
class SourcesSettings @Inject constructor(
    store: SettingsStore
) {
    val musicPermissionRequested = store.preference(MusicPermissionRequested)
    val excludedFolders = store.preference(ExcludedFolders)
    val extraFolders = store.preference(ExtraFolders)

    companion object {
        /** Whether the music permission has been asked for, so a later refusal without a rationale reads as permanent. */
        val MusicPermissionRequested = Setting.boolean("music_permission_requested", false)

        /** Absolute paths the scanner skips, even inside an included folder. */
        val ExcludedFolders = stringList("scanner_excluded_folders")

        /** SAF tree URIs the scanner walks directly, beside what MediaStore lists. Their grants are persisted. */
        val ExtraFolders = stringList("scanner_extra_folders")

        private fun stringList(key: String): Setting<List<String>> = Setting.string(
            key = key,
            default = emptyList(),
            decode = { value -> value.split('\n').filter { it.isNotEmpty() } },
            encode = { value -> value.joinToString("\n") }
        )
    }
}
