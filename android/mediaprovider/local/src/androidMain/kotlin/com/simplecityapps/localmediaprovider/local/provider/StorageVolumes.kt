package com.simplecityapps.localmediaprovider.local.provider

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume

/**
 * The root of each storage volume mounted now, ending in a separator (`/storage/emulated/0/`, `/storage/1234-5678/`), as
 * StorageManager lists them: every volume, USB drives included, where the app's files directories leave some out. Empty
 * if it can't say, which [unmountedRoots] reads as nothing mounted, so no song is taken for gone.
 */
fun mountedVolumeRoots(context: Context): Set<String> = context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty()
    .mapNotNull { volume -> volume.mountedRoot() }
    .toSet()

@Suppress("DEPRECATION")
private fun StorageVolume.mountedRoot(): String? = mountedRoot(
    state = state,
    directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) directory?.path else null,
    isPrimary = isPrimary,
    uuid = uuid,
    primaryDirectory = Environment.getExternalStorageDirectory().path
)

/**
 * The root of a volume in [state], ending in a separator, or null unless it's mounted: its [directory] where Android says
 * (API 30 on), else the shared storage's [primaryDirectory] for the [isPrimary] volume, or `/storage/<uuid>`, where
 * Android mounts every other, for one with a [uuid].
 */
internal fun mountedRoot(
    state: String,
    directory: String?,
    isPrimary: Boolean,
    uuid: String?,
    primaryDirectory: String
): String? {
    if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return null
    val root =
        directory ?: when {
            isPrimary -> primaryDirectory
            uuid != null -> "/storage/$uuid"
            else -> return null
        }
    return root.trimEnd('/') + "/"
}

/**
 * The roots of the stored songs at [songPaths] that an S2 scanner import couldn't read: those of volumes not among
 * [mountedRoots] ([unmountedRoots]), or every file path if MediaStore couldn't be listed ([mediaStoreListed] false),
 * and each of the extra folder trees in [unavailableTrees], whose songs keep a document URI under the tree as their path.
 */
fun scannerUnreadableRoots(
    songPaths: List<String>,
    mountedRoots: Set<String>,
    mediaStoreListed: Boolean,
    unavailableTrees: List<String>
): Set<String> = (if (mediaStoreListed) unmountedRoots(songPaths, mountedRoots) else setOf("/")) +
    unavailableTrees.map { treeUri -> "$treeUri/document/" }

/**
 * The volumes holding those of [paths] that no root in [mountedRoots] covers: an SD card that's out, say, whose songs
 * MediaStore stops listing until it's back. Each is the shortest folder of the path, two levels down or more, that doesn't
 * hold a mounted root (`/storage/1234-5678/`), ending in a separator. Paths that aren't absolute file paths, such as
 * document URIs, are left out.
 */
fun unmountedRoots(
    paths: Iterable<String>,
    mountedRoots: Set<String>
): Set<String> = paths
    .filter { path -> path.startsWith("/") && mountedRoots.none { root -> path.startsWith(root) } }
    .mapNotNull { path -> volumeRoot(path, mountedRoots) }
    .toSet()

private fun volumeRoot(
    path: String,
    mountedRoots: Set<String>
): String? {
    val folders = path.split('/').drop(1).dropLast(1)
    return (2..folders.size)
        .map { depth -> folders.take(depth).joinToString("/", prefix = "/", postfix = "/") }
        .firstOrNull { folder -> mountedRoots.none { root -> root.startsWith(folder) } }
}
