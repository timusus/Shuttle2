package com.simplecityapps.shuttle.model

import java.io.ByteArrayOutputStream

/**
 * Derives the folder containing a song from [Song.path], for browsing the local library by folder.
 *
 * A folder is identified by its segments, the first of which is the storage volume: [PRIMARY_VOLUME] for
 * internal shared storage, or the volume id (e.g. "1234-5678") for an SD card or USB drive. So a MediaStore
 * path (/storage/emulated/0/Music/a.mp3) and a SAF document URI for the same file
 * (content://com.android.externalstorage.documents/tree/…/document/primary%3AMusic%2Fa.mp3) both resolve to
 * [primary, Music].
 *
 * Paths that carry no usable hierarchy (other document providers, content://media URIs) resolve to the single
 * [OTHER_VOLUME] folder rather than failing.
 */
object SongFolder {
    const val PRIMARY_VOLUME = "primary"

    /** Fallback folder for songs whose path is not path-like. Can't collide with a real segment. */
    const val OTHER_VOLUME = "<other>"

    /** Volume for absolute paths outside /storage, e.g. /mnt/… or /data/…. */
    const val FILESYSTEM_ROOT = "/"

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"

    data class Location(
        val folder: List<String>,
        val fileName: String
    )

    fun locate(path: String): Location = when {
        path.startsWith("content://") -> locateDocumentUri(path)
        path.startsWith("file://") -> locateFilePath(percentDecode(path.removePrefix("file://")))
        path.startsWith("/") -> locateFilePath(path)
        else -> null
    } ?: Location(listOf(OTHER_VOLUME), fileNameFallback(path))

    /** True if the song at [path] is in [folder] or any of its subfolders. */
    fun isUnder(
        path: String,
        folder: List<String>
    ): Boolean {
        val songFolder = locate(path).folder
        return songFolder.size >= folder.size && songFolder.subList(0, folder.size) == folder
    }

    /**
     * Orders songs the way the folder browser lists them: depth first, subfolders (by name) before the songs
     * of a folder, songs by file name.
     */
    fun List<Song>.inFolderOrder(): List<Song> = map { song -> song to locate(song.path) }
        .sortedWith { a, b -> compareLocations(a.second, b.second) }
        .map { it.first }

    /** Case-insensitive natural order, so "Track 2" sorts before "Track 10". */
    val nameComparator: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    /** Orders folder segments, keeping the [OTHER_VOLUME] fallback after real volumes. */
    val segmentComparator: Comparator<String> = Comparator { a, b ->
        when {
            a == b -> 0
            a == OTHER_VOLUME -> 1
            b == OTHER_VOLUME -> -1
            else -> compareNatural(a, b)
        }
    }

    private fun compareLocations(
        a: Location,
        b: Location
    ): Int {
        val common = minOf(a.folder.size, b.folder.size)
        for (i in 0 until common) {
            val result = segmentComparator.compare(a.folder[i], b.folder[i])
            if (result != 0) return result
        }
        // One folder contains the other: songs in the subfolder come first
        if (a.folder.size != b.folder.size) return b.folder.size.compareTo(a.folder.size)
        return compareNatural(a.fileName, b.fileName)
    }

    private fun locateDocumentUri(uri: String): Location? {
        val authority = uri.removePrefix("content://").substringBefore('/')
        val documentIndex = uri.lastIndexOf("/document/")
        if (documentIndex == -1) return null
        val documentId = percentDecode(
            uri.substring(documentIndex + "/document/".length).substringBefore('?').substringBefore('#')
        )

        return when (authority) {
            EXTERNAL_STORAGE_AUTHORITY -> locateStorageDocumentId(documentId)
            DOWNLOADS_AUTHORITY -> documentId.takeIf { it.startsWith("raw:/") }?.let { locateFilePath(it.removePrefix("raw:")) }
            else -> null
        }
    }

    /** Document ids from the external storage provider look like "primary:Music/Artist/song.mp3". */
    private fun locateStorageDocumentId(documentId: String): Location? {
        val volume = documentId.substringBefore(':', missingDelimiterValue = "")
        if (volume.isEmpty()) return null
        val segments = documentId.substringAfter(':').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        return when (volume) {
            // The "home" root is primary:Documents
            "home" -> Location(listOf(PRIMARY_VOLUME, "Documents") + segments.dropLast(1), segments.last())
            else -> Location(listOf(volume) + segments.dropLast(1), segments.last())
        }
    }

    private fun locateFilePath(path: String): Location? {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val (volume, relative) = when {
            // /storage/emulated/0/…
            segments.size >= 3 && segments[0] == "storage" && segments[1] == "emulated" -> PRIMARY_VOLUME to segments.drop(3)
            // /storage/self/primary/…
            segments.size >= 3 && segments[0] == "storage" && segments[1] == "self" -> PRIMARY_VOLUME to segments.drop(3)
            // /storage/1234-5678/…
            segments.size >= 2 && segments[0] == "storage" -> segments[1] to segments.drop(2)
            // /sdcard/…
            segments[0] == "sdcard" -> PRIMARY_VOLUME to segments.drop(1)
            // /mnt/media_rw/1234-5678/…
            segments.size >= 3 && segments[0] == "mnt" && segments[1] == "media_rw" -> segments[2] to segments.drop(3)
            else -> FILESYSTEM_ROOT to segments
        }
        if (relative.isEmpty()) return null
        return Location(listOf(volume) + relative.dropLast(1), relative.last())
    }

    private fun fileNameFallback(path: String): String = percentDecode(path.substringAfterLast('/')).substringAfterLast('/').ifEmpty { path }

    /** Decodes %XX escapes as UTF-8. Unlike URLDecoder, leaves '+' alone and tolerates malformed escapes. */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val out = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val byte = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    i += 3
                    continue
                }
            }
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toString("UTF-8")
    }

    private fun compareNatural(
        a: String,
        b: String
    ): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length.compareTo(numB.length)
                val result = numA.compareTo(numB)
                if (result != 0) return result
            } else {
                val result = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (result != 0) return result
                i++
                j++
            }
        }
        val remaining = (a.length - i).compareTo(b.length - j)
        return if (remaining != 0) remaining else a.compareTo(b)
    }
}
