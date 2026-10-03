package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.shuttle.ui.screens.sources.FolderKind
import com.simplecityapps.shuttle.ui.screens.sources.FolderLists
import com.simplecityapps.shuttle.ui.screens.sources.ScannerFolderStore
import com.simplecityapps.shuttle.ui.screens.sources.SourceFolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The folders picked in Files, as Sources lists them: each is read in full, so they're [FolderKind.Extra]s, with the
 * folder's id as the [SourceFolder.uri]. iOS has no MediaStore to include from or exclude from, so those can't be added.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosScannerFolderStore(
    private val localFiles: IosLocalFiles
) : ScannerFolderStore {
    private val _folders = MutableStateFlow(read())
    override val folders: StateFlow<FolderLists> = _folders.asStateFlow()

    override fun add(
        kind: FolderKind,
        treeUri: String
    ): Boolean {
        if (kind != FolderKind.Extra) return false
        return localFiles.addFolder(treeUri).also { refresh() }
    }

    override fun remove(
        kind: FolderKind,
        folder: SourceFolder
    ) {
        folder.uri?.let(localFiles::removeFolder)
        refresh()
    }

    override fun refresh() {
        _folders.value = read()
    }

    private fun read() = FolderLists(
        extras = localFiles.folders().map { folder -> SourceFolder(uri = folder.id, path = folder.path, name = folder.name, hasAccess = folder.hasAccess) }
    )
}
