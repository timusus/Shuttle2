package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A scrobble waiting to be sent; its unique index makes enqueuing idempotent, so replaying a play never duplicates a row. */
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
        const val SERVICE_LISTENBRAINZ = "listenbrainz"
    }
}
