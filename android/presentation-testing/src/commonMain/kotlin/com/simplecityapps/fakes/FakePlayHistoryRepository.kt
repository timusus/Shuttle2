package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.playhistory.AlbumArtistCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps the plays recorded; the queries answer with what the test sets. */
class FakePlayHistoryRepository : PlayHistoryRepository {
    data class Play(
        val songId: Long,
        val startedAt: Instant,
        val listenedMs: Long,
        val completed: Boolean,
        val context: PlayContext
    )

    val plays = mutableListOf<Play>()

    var recentContexts: List<RecentContext> = emptyList()
    var contextsAroundHour: List<ContextDays> = emptyList()
    var albumCompletions: List<AlbumCompletions> = emptyList()
    var albumArtistCompletions: List<AlbumArtistCompletions> = emptyList()
    var genrePlays: List<GenrePlays> = emptyList()
    val eventCount = MutableStateFlow(0)

    override suspend fun recordPlay(
        song: Song,
        startedAt: Instant,
        listenedMs: Long,
        completed: Boolean,
        context: PlayContext
    ) {
        plays += Play(song.id, startedAt, listenedMs, completed, context)
    }

    override suspend fun recentContexts(limit: Int): List<RecentContext> = recentContexts.take(limit)

    override suspend fun contextsAroundHour(
        hour: Int,
        windowMinutes: Int,
        since: Instant,
        limit: Int
    ): List<ContextDays> = contextsAroundHour.take(limit)

    override suspend fun albumCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumCompletions> = albumCompletions.take(limit)

    override suspend fun albumArtistCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumArtistCompletions> = albumArtistCompletions.take(limit)

    override suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays> = genrePlays.take(limit)

    override fun eventCount(): Flow<Int> = eventCount

    override suspend fun clearHistory() {
        plays.clear()
    }
}
