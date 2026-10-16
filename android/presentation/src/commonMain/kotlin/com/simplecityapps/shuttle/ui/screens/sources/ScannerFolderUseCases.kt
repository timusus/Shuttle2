package com.simplecityapps.shuttle.ui.screens.sources

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.StateFlow

/** The folders picked in Sources, re-emitted as they change. */
class ObserveScannerFolders @Inject constructor(
    private val folderStore: ScannerFolderStore,
) {
    operator fun invoke(): StateFlow<FolderLists> = folderStore.folders
}
