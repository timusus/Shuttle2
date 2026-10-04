package com.simplecityapps.localmediaprovider.local.provider.taglib

/**
 * Which folders the TagLib scanner imports, as absolute paths. With no [includes], every folder is included; an
 * exclude wins over an include, so a subfolder of an included folder can be left out. Shared storage paths are
 * case-insensitive, so matching is too.
 */
data class FolderFilter(
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList()
) {
    fun accepts(path: String): Boolean = (includes.isEmpty() || includes.any { folder -> path.isIn(folder) }) && excludes.none { folder -> path.isIn(folder) }

    private fun String.isIn(folder: String): Boolean = startsWith(folder.trimEnd('/') + "/", ignoreCase = true)
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * The absolute folder path of a tree picked from the external storage document provider, or null for a tree from any
 * other provider (Downloads, cloud storage), which has no path to filter MediaStore's rows by. A document id has the
 * same form, so this also gives a document's file path.
 *
 * A tree document id is `<root>:<path>`: `primary` is [primaryStoragePath], `home` is its Documents folder, and any
 * other root is a secondary volume's id, mounted at `/storage/<id>`.
 */
fun externalStorageTreeFolder(
    authority: String?,
    treeDocumentId: String,
    primaryStoragePath: String
): String? {
    if (authority != EXTERNAL_STORAGE_AUTHORITY || ':' !in treeDocumentId) return null
    val root = treeDocumentId.substringBefore(':')
    val relativePath = treeDocumentId.substringAfter(':').trim('/')
    val rootPath =
        when (root) {
            "primary" -> primaryStoragePath.trimEnd('/')
            "home" -> "${primaryStoragePath.trimEnd('/')}/Documents"
            "" -> return null
            else -> "/storage/$root"
        }
    return if (relativePath.isEmpty()) rootPath else "$rootPath/$relativePath"
}

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
