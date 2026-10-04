package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.mediaprovider.DownloadSource

/**
 * The platform half of [OfflineDownloads]: fetches a song's file in the background, keeps it on the device keyed by the
 * song's path, and reports what happened to its [Listener]. iOS's is a background `URLSession` (`UrlSessionDownloads`),
 * whose downloads carry on while the app is suspended and are picked up again at the next launch.
 */
interface DownloadTransport {
    /** Receives every download's progress and outcome, on any thread. Set once, before [restore]. */
    var listener: Listener?

    /**
     * The paths whose files are on the device, read once at launch. Downloads still running from an earlier launch
     * report [Listener.onRunning] once they've been found, then their progress and outcome as usual.
     */
    fun restore(): Set<String>

    /** Downloads [path]'s file from [source], replacing any earlier file. */
    fun start(
        path: String,
        source: DownloadSource
    )

    /** Stops [path]'s download if it's running and deletes its file. */
    fun remove(path: String)

    /** The `file://` URL [path]'s completed download plays from, or null if it has none. */
    fun fileUrl(path: String): String?

    interface Listener {
        /** [path]'s download, started at an earlier launch, is still running. */
        fun onRunning(path: String)

        /** [totalBytes] is -1 while unknown. */
        fun onProgress(
            path: String,
            bytesWritten: Long,
            totalBytes: Long
        )

        /** [path]'s file is on the device. */
        fun onCompleted(path: String)

        /** [path]'s download failed: the server refused it, or the network or disk did. Not called for a [remove]. */
        fun onFailed(path: String)
    }
}
