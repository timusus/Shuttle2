package com.simplecityapps.shuttle.ui.screens.sources

import kotlinx.coroutines.flow.StateFlow

/** How the S2 scanner treats a folder picked in Sources. */
enum class FolderKind {
    /** Only these folders are scanned, when there are any. */
    Include,

    /** Never scanned, even inside an included folder. */
    Exclude,

    /** Read directly, for folders MediaStore skips (`.nomedia`) or formats it doesn't index (#415). */
    Extra
}

/**
 * A folder in Sources: [uri] is its SAF tree (null for an exclude, which keeps only the path), [path] its file path
 * when known. [hasAccess] is false once its SAF grant was revoked outside the app (#479); excludes need no grant,
 * so they're always true.
 */
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
