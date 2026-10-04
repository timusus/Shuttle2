package com.simplecityapps.localmediaprovider.local.provider

import android.content.Context

/**
 * The root of each storage volume mounted now, ending in a separator (`/storage/emulated/0/`, `/storage/1234-5678/`): the
 * volumes this app has a files directory on, which Android lists only while they're mounted.
 */
fun mountedVolumeRoots(context: Context): Set<String> = context.getExternalFilesDirs(null)
    .filterNotNull()
    .mapNotNull { dir -> dir.path.substringBefore("/Android/data/", missingDelimiterValue = "").takeIf { root -> root.isNotEmpty() } }
    .map { root -> "$root/" }
    .toSet()

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
