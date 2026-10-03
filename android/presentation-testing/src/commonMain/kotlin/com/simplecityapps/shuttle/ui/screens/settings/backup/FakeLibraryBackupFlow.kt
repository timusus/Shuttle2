package com.simplecityapps.shuttle.ui.screens.settings.backup

/** In-memory [LibraryBackupFlow]: tests set the JSON and report to return, no storage involved. */
class FakeLibraryBackupFlow : LibraryBackupFlow {
    var stagedJson: String? = "{\"staged\":true}"
    val written = mutableMapOf<String, String>()
    var report: RestoreReport? = RestoreReport(1, 0, 1, 1, emptyList(), 0)
    var writeSucceeds = true

    /** Thrown by every call when set. */
    var failure: Throwable? = null

    override suspend fun buildBackupJson(): String? = failure?.let { throw it } ?: stagedJson

    override suspend fun writeBackup(destinationUri: String, backupJson: String): Boolean {
        failure?.let { throw it }
        written[destinationUri] = backupJson
        return writeSucceeds
    }

    override suspend fun readAndRestore(sourceUri: String): RestoreReport? = failure?.let { throw it } ?: report
}
