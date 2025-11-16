package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.datetime.Instant

@Entity(
    tableName = "sync_operations",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistData::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlist_id"),
        Index("status"),
        Index(value = ["priority", "created_at"])
    ]
)
data class SyncOperation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "external_id")
    val externalId: String?,

    @ColumnInfo(name = "media_provider_type")
    val mediaProviderType: MediaProviderType,

    @ColumnInfo(name = "operation_type")
    val operationType: OperationType,

    @ColumnInfo(name = "operation_data")
    val operationData: String, // JSON-encoded operation-specific data

    @ColumnInfo(name = "status")
    val status: SyncStatus,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Instant? = null,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)

enum class OperationType {
    CREATE_PLAYLIST,
    UPDATE_METADATA,
    ADD_SONGS,
    REMOVE_SONGS,
    REORDER_SONGS,
    DELETE_PLAYLIST
}

enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED,
    CANCELLED
}
