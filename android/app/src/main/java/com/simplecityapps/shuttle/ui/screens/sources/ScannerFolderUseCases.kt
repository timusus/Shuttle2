package com.simplecityapps.shuttle.ui.screens.sources

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The folders picked in Sources, re-emitted as they change. */
class ObserveScannerFolders @Inject constructor(
    private val folderStore: ScannerFolderStore,
) {
    operator fun invoke(): StateFlow<FolderLists> = folderStore.folders
}

/** Adds the tree the folder picker returned as a [FolderKind] folder; false if it can't be used that way. */
class AddScannerFolder @Inject constructor(
    private val folderStore: ScannerFolderStore,
) {
    operator fun invoke(kind: FolderKind, treeUri: String): Boolean = folderStore.add(kind, treeUri)
}

/** Removes a folder from Sources. */
class RemoveScannerFolder @Inject constructor(
    private val folderStore: ScannerFolderStore,
) {
    operator fun invoke(kind: FolderKind, folder: SourceFolder) = folderStore.remove(kind, folder)
}

/** Reads the Sources folders again, for grants changed outside the app. */
class RefreshScannerFolders @Inject constructor(
    private val folderStore: ScannerFolderStore,
) {
    operator fun invoke() = folderStore.refresh()
}
