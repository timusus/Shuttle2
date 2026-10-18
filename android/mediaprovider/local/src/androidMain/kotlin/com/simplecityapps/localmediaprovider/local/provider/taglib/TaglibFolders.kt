package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * The folders the S2 scanner covers, read at the start of each import: [filter] limits MediaStore's audio rows;
 * [includeTrees] are the SAF trees of the included folders, walked when MediaStore's listing of them can't be trusted
 * alone; and [extraTrees] are SAF trees always walked directly, for folders MediaStore skips (`.nomedia`) or formats it
 * doesn't index.
 */
data class ScannerFolders(
    val filter: FolderFilter = FolderFilter(),
    val includeTrees: List<Uri> = emptyList(),
    val extraTrees: List<Uri> = emptyList()
)

/** A tree by its authority and document id, which is the same however the grant that names it was encoded. */
internal fun treeKey(tree: Uri): String = "${tree.authority}/${try {
    DocumentsContract.getTreeDocumentId(tree)
} catch (e: IllegalArgumentException) {
    tree.toString()
}}"

/**
 * Whether MediaStore indexes the volume holding the tree at [treeDocumentId] (`primary:Music`, `1234-5678:Music`): shared
 * storage always, any other volume (an SD card, a USB drive) only if it's among [mediaStoreVolumes], which name volumes by
 * their lowercase uuid.
 */
internal fun mediaStoreIndexesTree(
    treeDocumentId: String,
    mediaStoreVolumes: Set<String>
): Boolean {
    val root = treeDocumentId.substringBefore(':')
    return root == "primary" || root == "home" || root.lowercase() in mediaStoreVolumes
}

/** The folder of [tree] if it has a file path (it's on a volume of the external storage provider), else null. */
internal fun treeFolder(
    tree: Uri,
    primaryStoragePath: String
): String? = try {
    externalStorageTreeFolder(tree.authority, DocumentsContract.getTreeDocumentId(tree), primaryStoragePath)
} catch (e: IllegalArgumentException) {
    null
}

/** The folder of [tree] if it's on a volume MediaStore indexes, else null. */
internal fun indexedTreeFolder(
    tree: Uri,
    primaryStoragePath: String,
    mediaStoreVolumes: Set<String>
): String? = try {
    val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
    externalStorageTreeFolder(tree.authority, treeDocumentId, primaryStoragePath)
        ?.takeIf { mediaStoreIndexesTree(treeDocumentId, mediaStoreVolumes) }
} catch (e: IllegalArgumentException) {
    null
}

/** The volumes MediaStore indexes besides shared storage, by name; before Android 10 it can't say, so none. */
internal fun mediaStoreVolumes(context: Context): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(context) else emptySet()

@Suppress("DEPRECATION")
internal fun primaryStoragePath(): String = Environment.getExternalStorageDirectory().path
