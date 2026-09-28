package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlin.time.Instant

/**
 * One play of a song (#633), written when it reached its listen threshold (#651) and marked [completed] if it then played
 * through; [listenedMs] is how long it was listened to by then (or in all, once completed).
 * The song is keyed by [mediaProvider] and [songPath], the songs table's own unique key, not its row id, which a reimport
 * replaces; a remote song's path is built from its server id (`jellyfin://item/<id>`), so it survives one too.
 * [localHour] (0 to 23) and [weekday] (ISO, 1 Monday to 7 Sunday) are the device's local ones at [startedAt], fixed at
 * write time. [contextType] and [contextId] are the [com.simplecityapps.shuttle.model.PlayContext] the queue was
 * started from.
 */
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
