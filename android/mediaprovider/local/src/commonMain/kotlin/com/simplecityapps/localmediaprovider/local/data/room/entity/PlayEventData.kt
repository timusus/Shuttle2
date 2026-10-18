package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/** One play of a song, keyed by path rather than row id, which a reimport replaces; local hour and weekday are fixed at write time. */
@Entity(
    tableName = "play_events",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["contextType", "contextId"])
    ]
)
data class PlayEventData(
    val mediaProvider: MediaProviderType,
    val songPath: String,
    val startedAt: Instant,
    val listenedMs: Long,
    val completed: Boolean,
    val localHour: Int,
    val weekday: Int,
    val contextType: String,
    val contextId: String?,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
