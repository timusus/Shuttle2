package com.simplecityapps.shuttle.storage

/**
 * The document id of the file at [path] inside the tree whose root document is [treeDocumentId], found at [treePath];
 * null if the file isn't in that tree. Document ids of the external storage provider are `<root>:<relative path>`, and
 * shared storage paths are case-insensitive, so the folder matches whatever its case.
 */
fun documentIdForPath(
    path: String,
    treeDocumentId: String,
    treePath: String
): String? {
    val folder = treePath.trimEnd('/')
    if (!path.startsWith("$folder/", ignoreCase = true)) return null
    val relative = path.substring(folder.length + 1)
    return if (treeDocumentId.endsWith(':')) treeDocumentId + relative else "${treeDocumentId.trimEnd('/')}/$relative"
}
