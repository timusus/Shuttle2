package com.simplecityapps.shuttle.ui.screens.settings.backup

/**
 * Platform backup flow behind the Library settings rows. Lives in presentation so the shared
 * [com.simplecityapps.shuttle.ui.screens.settings.SettingsViewModel] can stage exports and merge
 * imports without touching Android storage APIs; the Android app module provides the implementation
 * over Room and the Storage Access Framework.
 *
 * Documents cross the boundary as strings (content-Uri text): the ViewModel never sees platform types.
 */
interface LibraryBackupFlow {
    /** Builds the full backup JSON, or null when the library can't be read. */
    suspend fun buildBackupJson(): String?

    /** Writes staged [backupJson] to [destinationUri]. */
    suspend fun writeBackup(destinationUri: String, backupJson: String): Boolean

    /** Reads the backup at [sourceUri] and merges it, or null when it can't be read or is too new. */
    suspend fun readAndRestore(sourceUri: String): RestoreReport?
}

data class RestoreReport(
    val songsMatched: Int,
    val songsUnmatched: Int,
    val statsWritten: Int,
    val playlistsRestored: Int,
    val playlistsUnresolved: List<String>,
    val membersSkipped: Int
)
