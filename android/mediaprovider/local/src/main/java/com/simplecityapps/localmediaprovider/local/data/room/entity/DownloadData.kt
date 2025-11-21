package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.DownloadState
import java.util.Date

/**
 * Room entity for tracking downloaded songs for offline playback
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index("song_id", unique = true),
        Index("download_state")
    ]
)
data class DownloadData(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "download_state")
    val downloadState: DownloadState,

    @ColumnInfo(name = "local_path")
    val localPath: String? = null,

    @ColumnInfo(name = "download_progress")
    val downloadProgress: Float = 0f,

    @ColumnInfo(name = "downloaded_bytes")
    val downloadedBytes: Long = 0,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long = 0,

    @ColumnInfo(name = "downloaded_date")
    val downloadedDate: Date? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date()
)
