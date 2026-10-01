package com.simplecityapps.shuttle.ui.screens.settings.backup

/** In-memory [LibraryBackupFlow]: tests stage JSON and reports, no storage involved. */
class FakeLibraryBackupFlow : LibraryBackupFlow {
    var stagedJson: String? = "{\"staged\":true}"
    val written = mutableMapOf<String, String>()
    var report: RestoreReport? = RestoreReport(1, 0, 1, 1, emptyList(), 0)

    override suspend fun buildBackupJson(): String? = stagedJson

    override suspend fun writeBackup(destinationUri: String, backupJson: String): Boolean {
        written[destinationUri] = backupJson
        return true
    }

    override suspend fun readAndRestore(sourceUri: String): RestoreReport? = report
}
