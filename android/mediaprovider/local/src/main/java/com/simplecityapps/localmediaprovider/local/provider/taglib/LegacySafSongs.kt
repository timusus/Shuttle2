package com.simplecityapps.localmediaprovider.local.provider.taglib

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.Song
import java.net.URLDecoder
import kotlin.math.abs

/**
 * Until #370 the TagLib scanner walked SAF folder grants and stored each song under its document URI, such as
 * `content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FAlbum%2FSong.mp3`.
 * It now finds files through MediaStore and stores their file path, so without a remap an upgrade would replace every
 * such song with a new row and lose its history.
 *
 * A song matches the MediaStore file on the same volume at the same relative path. Size, last modified and duration
 * decide only when the path alone is ambiguous: the volume isn't mounted under its old id but the relative path
 * exists on another one, several files share the path, or the document id has no path at all. Anything else stays
 * unmatched, and the import removes it as missing.
 *
 * @param primaryStoragePath where the primary shared storage volume is mounted, `/storage/emulated/0` for the main user
 */
internal class LegacySafSongs(primaryStoragePath: String) {
    private val primaryStoragePrefix = primaryStoragePath.trimEnd('/') + "/"

    /**
     * The remaps for the [songs] stored under a SAF document URI that match one of [files]. When several songs match
     * the same file, the one with the most plays keeps its row and the rest are its duplicates.
     */
    fun remaps(
        songs: List<Song>,
        files: List<MediaStoreAudioFile>
    ): List<SongPathRemap> {
        val legacySongs = songs.mapNotNull { song -> legacyLocation(song.path)?.let { location -> song to location } }
        if (legacySongs.isEmpty()) return emptyList()

        val filesByVolumePath = files.groupBy { file -> volumePath(file.path) }
        val filesByRelativePath = files.groupBy { file -> volumePath(file.path)?.substringAfter('/') }
        val filesByPath = files.groupBy { file -> file.path.lowercase() }
        val filesById = files.associateBy { file -> file.id }

        return legacySongs
            .mapNotNull { (song, location) ->
                val candidates =
                    when (location) {
                        is LegacyLocation.OnVolume -> {
                            val key = "${location.volume}/${location.relativePath}".lowercase()
                            filesByVolumePath[key]?.let { exact -> Candidates(exact, exact = true) }
                                ?: Candidates(filesByRelativePath[location.relativePath.lowercase()].orEmpty(), exact = false)
                        }

                        is LegacyLocation.AbsolutePath -> Candidates(filesByPath[location.path.lowercase()].orEmpty(), exact = true)

                        // MediaStore ids change when its database is rebuilt, so an id alone doesn't prove it's the same file
                        is LegacyLocation.MediaStoreId -> Candidates(listOfNotNull(filesById[location.id]), exact = false)

                        LegacyLocation.Unknown -> Candidates(files, exact = false)
                    }
                candidates.match(song)?.let { file -> song to file }
            }
            .groupBy(keySelector = { (_, file) -> file.path }, valueTransform = { (song, _) -> song })
            .map { (path, matchedSongs) ->
                val (keeper, duplicates) = matchedSongs.sortedWith(mostPlayedFirst).let { sorted -> sorted.first() to sorted.drop(1) }
                SongPathRemap(songId = keeper.id, path = path, duplicateIds = duplicates.map { song -> song.id })
            }
    }

    private data class Candidates(
        val files: List<MediaStoreAudioFile>,
        val exact: Boolean
    ) {
        fun match(song: Song): MediaStoreAudioFile? = if (exact && files.size == 1) {
            files.single()
        } else {
            files.singleOrNull { file -> file.isSameFileAs(song) }
        }
    }

    /** `<volume>/<relative path>` in lower case, with the primary volume as `primary`, or null off shared storage. */
    private fun volumePath(path: String): String? = when {
        path.startsWith(primaryStoragePrefix, ignoreCase = true) -> "$PRIMARY_VOLUME/${path.substring(primaryStoragePrefix.length)}"
        path.startsWith(STORAGE_PREFIX) -> path.substring(STORAGE_PREFIX.length).takeIf { '/' in it }
        else -> null
    }?.lowercase()

    private companion object {
        const val PRIMARY_VOLUME = "primary"
        const val STORAGE_PREFIX = "/storage/"

        // The old scanner read size and last modified from the same file MediaStore did, but MediaStore keeps whole
        // seconds and computes its own duration
        const val LAST_MODIFIED_TOLERANCE_MS = 2_000L
        const val DURATION_TOLERANCE_MS = 2_000L

        val mostPlayedFirst: Comparator<Song> =
            compareByDescending<Song> { song -> song.playCount }
                .thenByDescending { song -> song.lastPlayed }
                .thenBy { song -> song.id }

        fun MediaStoreAudioFile.isSameFileAs(song: Song): Boolean {
            val songLastModified = song.lastModified?.toEpochMilliseconds() ?: return false
            return size == song.size &&
                abs(lastModified - songLastModified) <= LAST_MODIFIED_TOLERANCE_MS &&
                (duration == null || abs(duration - song.duration) <= DURATION_TOLERANCE_MS)
        }
    }
}

/** Where a song stored under a SAF document URI lives, as far as its document id says. */
internal sealed interface LegacyLocation {
    /** [volume] is `primary` or a secondary volume's id, such as `04B9-1208`. */
    data class OnVolume(
        val volume: String,
        val relativePath: String
    ) : LegacyLocation

    data class AbsolutePath(val path: String) : LegacyLocation

    data class MediaStoreId(val id: Long) : LegacyLocation

    /** A document from a provider whose ids say nothing about the file, so only its size, date and duration can match it. */
    data object Unknown : LegacyLocation
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
private const val DOCUMENT_SEGMENT = "/document/"

/**
 * Decodes a SAF document URI the old scanner stored as a song's path into where the file lives, or null if [path]
 * isn't a document URI, which means the song is already keyed by its file path.
 *
 * External storage document ids are `<root>:<path>`: `primary` for the primary volume, `home` for its Documents
 * folder and a volume id for a secondary volume. The Downloads provider uses `raw:<absolute path>` or `msf:<MediaStore
 * id>`. Other ids are opaque.
 */
internal fun legacyLocation(path: String): LegacyLocation? {
    if (!path.startsWith("content://") || DOCUMENT_SEGMENT !in path) return null
    val authority = path.removePrefix("content://").substringBefore('/')
    // Slashes inside a document id are encoded, so the last document segment is the id
    val documentId = percentDecode(path.substringAfterLast(DOCUMENT_SEGMENT))
    if (documentId.isEmpty()) return null
    val root = documentId.substringBefore(':', missingDelimiterValue = "")
    val rest = documentId.substringAfter(':', missingDelimiterValue = "")
    return when (authority) {
        EXTERNAL_STORAGE_AUTHORITY -> {
            val relativePath = rest.trim('/')
            when {
                root.isEmpty() || relativePath.isEmpty() -> LegacyLocation.Unknown
                root == "primary" -> LegacyLocation.OnVolume("primary", relativePath)
                root == "home" -> LegacyLocation.OnVolume("primary", "Documents/$relativePath")
                else -> LegacyLocation.OnVolume(root, relativePath)
            }
        }

        DOWNLOADS_AUTHORITY ->
            when (root) {
                "raw" -> LegacyLocation.AbsolutePath(rest)
                "msf" -> rest.toLongOrNull()?.let(LegacyLocation::MediaStoreId) ?: LegacyLocation.Unknown
                else -> LegacyLocation.Unknown
            }

        else -> LegacyLocation.Unknown
    }
}

// Uri.encode never leaves a literal '+', but URLDecoder would read one as a space
private fun percentDecode(encoded: String): String = runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), Charsets.UTF_8.name()) }.getOrDefault(encoded)
