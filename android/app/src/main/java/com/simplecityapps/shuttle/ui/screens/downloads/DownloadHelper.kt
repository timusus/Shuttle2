package com.simplecityapps.shuttle.ui.screens.downloads

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.Song

/**
 * Helper object for download-related UI functionality
 */
object DownloadHelper {

    /**
     * Get the appropriate icon for a song's download state
     */
    fun getDownloadIcon(downloadState: DownloadState): ImageVector {
        return when (downloadState) {
            DownloadState.COMPLETED -> Icons.Default.DownloadDone
            else -> Icons.Default.Download
        }
    }

    /**
     * Get the download action text for a song's download state
     */
    fun getDownloadActionText(downloadState: DownloadState): String {
        return when (downloadState) {
            DownloadState.NONE -> "Download"
            DownloadState.QUEUED -> "Queued for download"
            DownloadState.DOWNLOADING -> "Downloading..."
            DownloadState.PAUSED -> "Resume download"
            DownloadState.COMPLETED -> "Downloaded"
            DownloadState.FAILED -> "Retry download"
        }
    }

    /**
     * Check if a song can be downloaded (is from a remote provider)
     */
    fun canDownload(song: Song): Boolean {
        return song.mediaProvider.remote
    }

    /**
     * Format download size for display
     */
    fun formatDownloadSize(bytes: Long): String {
        return when {
            bytes == 0L -> "Unknown size"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Get download status message
     */
    fun getDownloadStatusMessage(downloadState: DownloadState, progress: Float, downloadedBytes: Long, totalBytes: Long): String {
        return when (downloadState) {
            DownloadState.NONE -> "Not downloaded"
            DownloadState.QUEUED -> "Waiting to download..."
            DownloadState.DOWNLOADING -> {
                val percentage = (progress * 100).toInt()
                if (totalBytes > 0) {
                    "$percentage% (${formatDownloadSize(downloadedBytes)} / ${formatDownloadSize(totalBytes)})"
                } else {
                    "$percentage%"
                }
            }
            DownloadState.PAUSED -> {
                val percentage = (progress * 100).toInt()
                "Paused at $percentage%"
            }
            DownloadState.COMPLETED -> {
                if (totalBytes > 0) {
                    "Downloaded (${formatDownloadSize(totalBytes)})"
                } else {
                    "Downloaded"
                }
            }
            DownloadState.FAILED -> "Download failed"
        }
    }
}
