package com.simplecityapps.localmediaprovider.local.data.room.entity

import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

/** A song's play stats and favourite: the columns a play or a favourite writes, which leave `library_generation` be. */
data class SongStatsData(
    val id: Long,
    val playCount: Int,
    val lastPlayed: Instant?,
    val lastCompleted: Instant?,
    val playbackPosition: Int,
    val favouritedAt: Instant?
)

const val SONG_STATS_COLUMNS = "playCount, lastPlayed, lastCompleted, playbackPosition, favouritedAt"

/** The songs whose stats aren't the defaults: a song without a row was never played, positioned or favourited. */
const val SONG_STATS_QUERY =
    "SELECT id, $SONG_STATS_COLUMNS FROM songs " +
        "WHERE playCount != 0 OR lastPlayed IS NOT NULL OR lastCompleted IS NOT NULL OR playbackPosition != 0 OR favouritedAt IS NOT NULL"

/** These songs with [stats]' play stats and favourite, a song absent from [stats] taking the defaults; a song whose stats match is kept as is. */
fun List<Song>.withStats(stats: List<SongStatsData>): List<Song> {
    val byId = stats.associateBy { it.id }
    return map { song ->
        val songStats = byId[song.id]
        val playCount = songStats?.playCount ?: 0
        val lastPlayed = songStats?.lastPlayed
        val lastCompleted = songStats?.lastCompleted
        val playbackPosition = songStats?.playbackPosition ?: 0
        val favouritedAt = songStats?.favouritedAt
        if (song.playCount == playCount && song.lastPlayed == lastPlayed && song.lastCompleted == lastCompleted && song.playbackPosition == playbackPosition && song.favouritedAt == favouritedAt) {
            song
        } else {
            song.copy(playCount = playCount, lastPlayed = lastPlayed, lastCompleted = lastCompleted, playbackPosition = playbackPosition, favouritedAt = favouritedAt)
        }
    }
}
