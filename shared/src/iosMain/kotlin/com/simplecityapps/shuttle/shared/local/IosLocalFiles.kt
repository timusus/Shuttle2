package com.simplecityapps.shuttle.shared.local

/**
 * This device's music files (#590), which only Swift can reach: the app's Documents folder (the Files app's "On My
 * iPhone > Shuttle Music", and Finder's file sharing) and the folders picked in Files, each kept as a security-scoped
 * bookmark. Swift's `LocalLibrary` implements it; the graph's factory is given one.
 *
 * A file's song path is `s2local://<folder id>/<path in the folder>`, unescaped: `documents` for the Documents folder,
 * a picked folder's own id otherwise. It stays the same when the app's container moves (every update and reinstall),
 * so a song keeps its history; [fileUrl] resolves it to where the file is now.
 *
 * Every call is synchronous and may block on the file system, so the import calls it off the main thread.
 */
interface IosLocalFiles {
    /** The picked folders, in the order they were added. The Documents folder is always read, so isn't listed. */
    fun folders(): List<IosLocalFolder>

    /** Keeps the folder the Files picker returned, by its file [url]; false if it can't be kept. Picking a kept folder again renews its access. */
    fun addFolder(url: String): Boolean

    fun removeFolder(id: String)

    /** Every audio file in Documents and the picked folders this app can reach. */
    fun audioFiles(): List<IosLocalFileRef>

    /** The file at song [path]'s tags and audio properties, or null when it can't be read or isn't audio. */
    fun readTags(path: String): IosLocalTags?

    /** A `file://` URL for song [path], or null when its folder is gone or out of reach. */
    fun fileUrl(path: String): String?

    /** No files: for tests and graphs without a device to read. */
    object None : IosLocalFiles {
        override fun folders(): List<IosLocalFolder> = emptyList()

        override fun addFolder(url: String): Boolean = false

        override fun removeFolder(id: String) = Unit

        override fun audioFiles(): List<IosLocalFileRef> = emptyList()

        override fun readTags(path: String): IosLocalTags? = null

        override fun fileUrl(path: String): String? = null
    }

    companion object {
        /** The scheme of a local song's path. */
        const val SCHEME = "s2local"

        /** The Documents folder's id in song paths. */
        const val DOCUMENTS = "documents"
    }
}

/** A folder picked in Files: [path] is where it was last found, [hasAccess] false while its bookmark can't be resolved. */
data class IosLocalFolder(
    val id: String,
    val name: String,
    val path: String?,
    val hasAccess: Boolean
)

/** An audio file: its song [path] (see [IosLocalFiles]), when it was last modified, in epoch milliseconds, and its size. */
data class IosLocalFileRef(
    val path: String,
    val lastModifiedMs: Long,
    val size: Long
)

/**
 * A file's tags and audio properties, as the S2Playback package's `AudioFileTags` reads them with FFmpeg: null or empty
 * where the file has no such tag, mapped as Android's TagLib reader maps them.
 */
data class IosLocalTags(
    val title: String?,
    val artists: List<String>,
    val artistDisplay: String?,
    val artistsTag: List<String>,
    val albumArtist: String?,
    val albumArtists: List<String>,
    val album: String?,
    val track: Int?,
    val disc: Int?,
    val year: Int?,
    val genres: List<String>,
    val replayGainTrack: Double?,
    val replayGainAlbum: Double?,
    val lyrics: String?,
    val grouping: String?,
    val compilation: Boolean?,
    val mbTrackId: String?,
    val mbAlbumId: String?,
    val mbReleaseGroupId: String?,
    val mbArtistIds: List<String>,
    val mbAlbumArtistIds: List<String>,
    val durationMs: Long?,
    val sampleRate: Int?,
    val channelCount: Int?,
    val bitDepth: Int?,
    /** Kilobits per second. */
    val bitRate: Int?,
    /** libavcodec's name for the codec: "flac", "alac", "mp3". */
    val codec: String?
)
