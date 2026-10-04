package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scrobble waiting to be sent (#503 slice 2, docs/architecture/scrobbling.md). Lives in the module's own
 * [ScrobbleDatabase], not the main media database, so scrobbling never migrates it. One row per service (only
 * "lastfm" today; ListenBrainz is a later slice) so an outage on one service never blocks another. The unique
 * index on ([service], [startedAtEpochSec], [track]) makes enqueuing idempotent: replaying the same play never
 * duplicates a row.
 */
@Entity(
    tableName = "queued_scrobbles",
    indices = [Index(value = ["service", "startedAtEpochSec", "track"], unique = true)]
)
data class QueuedScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val service: String,
    val artist: String,
    val track: String,
    val album: String?,
    val albumArtist: String?,
    val durationMs: Int,
    val startedAtEpochSec: Long
) {
    companion object {
        const val SERVICE_LASTFM = "lastfm"
    }
}
