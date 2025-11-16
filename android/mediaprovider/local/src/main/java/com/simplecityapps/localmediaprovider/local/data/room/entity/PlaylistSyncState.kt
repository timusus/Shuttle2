package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "playlist_sync_state",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistData::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSyncState(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Instant? = null,

    @ColumnInfo(name = "local_modified_at")
    val localModifiedAt: Instant,

    @ColumnInfo(name = "remote_modified_at")
    val remoteModifiedAt: Instant? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: PlaylistSyncStatus,

    @ColumnInfo(name = "conflict_detected")
    val conflictDetected: Boolean = false,

    @ColumnInfo(name = "local_content_hash")
    val localContentHash: String,

    @ColumnInfo(name = "remote_content_hash")
    val remoteContentHash: String? = null
)

enum class PlaylistSyncStatus {
    SYNCED,
    LOCAL_AHEAD,
    REMOTE_AHEAD,
    CONFLICT,
    ERROR
}
