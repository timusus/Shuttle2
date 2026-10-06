package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * The id a playlist imported from the m3u file at [uri] is stored under: its file [path] as a `file://` URI wherever it
 * has one, so it's the same playlist whether a granted folder or MediaStore gave the file, after MediaStore indexes it
 * again, and when a folder's walk fails. A document with no file path (cloud storage, Downloads) keeps its document URI.
 */
internal fun playlistFileId(
    uri: Uri,
    path: String?
): String = path?.let { Uri.fromFile(File(it)).toString() } ?: uri.toString()

/**
 * The id of the playlist file a stored [id] names now ([playlistFileId]): one stored as a shared storage document URI,
 * before playlists were known by their file path, is its file's; any other is itself.
 */
internal fun currentPlaylistFileId(
    id: String,
    primaryStoragePath: String
): String = id.takeIf { it.startsWith("content://") }?.let { songFilePath(it, primaryStoragePath) }?.let { path -> playlistFileId(Uri.parse(id), path) } ?: id

/**
 * The file path of the song stored at [path]: [path] itself, or the file a shared storage document URI names; null for a
 * document with no file path.
 */
internal fun songFilePath(
    path: String,
    primaryStoragePath: String
): String? = when {
    path.startsWith("/") -> path

    path.startsWith("content://") ->
        try {
            val uri = Uri.parse(path)
            externalStorageTreeFolder(uri.authority, DocumentsContract.getDocumentId(uri), primaryStoragePath)
        } catch (e: IllegalArgumentException) {
            null
        }

    else -> null
}
