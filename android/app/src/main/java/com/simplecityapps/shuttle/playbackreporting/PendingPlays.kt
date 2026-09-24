package com.simplecityapps.shuttle.playbackreporting

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.time.Instant

/**
 * Songs that played through while their play couldn't be reported, kept across restarts until
 * [com.simplecityapps.mediaprovider.PlaybackReporter.markPlayed] records them. Holds at most
 * [capacity] plays, dropping the oldest.
 */
class PendingPlays(
    private val sharedPreferences: SharedPreferences,
    private val capacity: Int = CAPACITY
) {
    data class Play(
        val songId: Long,
        val playedAt: Instant
    )

    fun all(): List<Play> = sharedPreferences.getString(KEY, null)
        ?.split(PLAY_SEPARATOR)
        ?.mapNotNull { encoded -> decode(encoded) }
        .orEmpty()

    fun add(play: Play) {
        save((all() + play).takeLast(capacity))
    }

    fun remove(plays: Collection<Play>) {
        if (plays.isEmpty()) return
        save(all() - plays.toSet())
    }

    private fun save(plays: List<Play>) {
        sharedPreferences.edit {
            if (plays.isEmpty()) {
                remove(KEY)
            } else {
                putString(KEY, plays.joinToString(PLAY_SEPARATOR) { play -> "${play.songId}$FIELD_SEPARATOR${play.playedAt.toEpochMilliseconds()}" })
            }
        }
    }

    private fun decode(encoded: String): Play? {
        val songId = encoded.substringBefore(FIELD_SEPARATOR).toLongOrNull() ?: return null
        val playedAtMs = encoded.substringAfter(FIELD_SEPARATOR, "").toLongOrNull() ?: return null
        return Play(songId, Instant.fromEpochMilliseconds(playedAtMs))
    }

    companion object {
        const val CAPACITY = 200
        private const val KEY = "playback_report_pending_plays"
        private const val PLAY_SEPARATOR = ","
        private const val FIELD_SEPARATOR = ":"
    }
}
