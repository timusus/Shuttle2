package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/** Writes restored per-song stats (play counts, exclusion, favourites) back to the library, e.g. from a library backup. */
interface SongStatsStore {
    /** Writes every restore in one transaction. */
    suspend fun restoreStats(restores: List<SongStatsRestore>)
}

/** One song's merged stats for [SongStatsStore.restoreStats]: [song] is the on-device state they were merged against. */
data class SongStatsRestore(
    val song: Song,
    val playCount: Int,
    val lastPlayed: Instant?,
    val lastCompleted: Instant?,
    val playbackPosition: Int,
    val dateAdded: Instant?,
    val excluded: Boolean,
    val favouritedAt: Instant?
)
