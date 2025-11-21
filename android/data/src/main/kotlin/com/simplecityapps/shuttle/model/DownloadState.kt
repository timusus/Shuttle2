package com.simplecityapps.shuttle.model

/**
 * Represents the download state of a song for offline playback
 */
enum class DownloadState {
    /** Song is not downloaded */
    NONE,

    /** Song is queued for download */
    QUEUED,

    /** Song is currently being downloaded */
    DOWNLOADING,

    /** Song download is paused */
    PAUSED,

    /** Song has been successfully downloaded and is available offline */
    COMPLETED,

    /** Song download failed */
    FAILED;

    val isDownloaded: Boolean
        get() = this == COMPLETED

    val isInProgress: Boolean
        get() = this == DOWNLOADING || this == QUEUED || this == PAUSED
}
