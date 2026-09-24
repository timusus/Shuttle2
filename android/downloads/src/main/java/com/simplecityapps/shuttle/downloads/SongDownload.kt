package com.simplecityapps.shuttle.downloads

/**
 * A song's download, keyed by [path] (`Song.path`), which is also the key its bytes are cached
 * under: the path is stable across re-imports, where the stream URL carries a token that rotates.
 */
data class SongDownload(
    val path: String,
    val state: State,
    /** 0..1; 0 while the length is still unknown, 1 once completed. */
    val progress: Float,
    val bytesDownloaded: Long,
    /** The total size in bytes, or -1 while unknown. */
    val contentLength: Long
) {
    enum class State {
        Queued,
        Downloading,
        Completed,
        Failed,
        Stopped,
        Removing
    }
}
