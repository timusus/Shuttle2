package com.simplecityapps.shuttle.ui.screens.sources

import kotlinx.coroutines.flow.StateFlow

/** How the S2 scanner treats a folder picked in Sources. */
enum class FolderKind {
    /** Only these folders are scanned, when there are any. */
    Include,

    /** Never scanned, even inside an included folder. */
    Exclude,

    /** Read directly, for folders MediaStore skips (`.nomedia`) or doesn't index. */
    Extra
}

/** [hasAccess] turns false once the SAF grant is revoked outside the app; excludes need no grant. */
data class SourceFolder(val uri: String?, val path: String?, val name: String, val hasAccess: Boolean = true)

data class FolderLists(
    val includes: List<SourceFolder> = emptyList(),
    val excludes: List<SourceFolder> = emptyList(),
    val extras: List<SourceFolder> = emptyList()
)

/**
 * The folders picked in Sources, which the S2 scanner reads at the start of each import: Android's `SafScannerFolderStore` keeps SAF grants.
 */
interface ScannerFolderStore {
    val folders: StateFlow<FolderLists>

    /** Adds the tree the folder picker returned; false if the folder can't be used that way (an exclude needs a file path). */
    fun add(kind: FolderKind, treeUri: String): Boolean

    fun remove(kind: FolderKind, folder: SourceFolder)

    /** Reads the folders again, for grants changed outside the app. */
    fun refresh()
}
