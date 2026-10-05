package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps the plays recorded, each one's id its index; the queries answer with what the test sets. */
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
    var albumDays: List<AlbumDay> = emptyList()
    var genrePlays: List<GenrePlays> = emptyList()
    val eventCount = MutableStateFlow(0)

    /** Each context's resume point, as last saved. */
    val resumePoints = mutableMapOf<PlayContext, ResumePoint>()

    /** Every resume point saved, in order. */
    val savedResumePoints = mutableListOf<ResumePoint>()

    /** Whether saving a resume point throws, as a failed database write would. */
    var failResumePointSaves = false

    /** The windowed aggregates asked for, with their arguments. */
    val queries = mutableListOf<String>()

    override suspend fun recordPlay(
        song: Song,
        startedAt: Instant,
        listenedMs: Long,
        completed: Boolean,
        context: PlayContext
    ): Long {
        plays += Play(song.id, startedAt, listenedMs, completed, context)
        return plays.lastIndex.toLong()
    }

    override suspend fun completePlay(
        id: Long,
        listenedMs: Long
    ) {
        plays[id.toInt()] = plays[id.toInt()].copy(listenedMs = listenedMs, completed = true)
    }

    override suspend fun recentContexts(limit: Int): List<RecentContext> = recentContexts.take(limit)

    override suspend fun contextsAroundHour(
        hour: Int,
        windowMinutes: Int,
        since: Instant,
        limit: Int
    ): List<ContextDays> = contextsAroundHour.take(limit).also { queries += "contextsAroundHour($hour, $windowMinutes, $since)" }

    override suspend fun albumDays(since: Instant): List<AlbumDay> = albumDays.also { queries += "albumDays($since)" }

    override suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays> = genrePlays.take(limit)

    override suspend fun saveResumePoint(point: ResumePoint) {
        if (failResumePointSaves) throw IllegalStateException("disk I/O error")
        if (point.context == PlayContext.None) return
        resumePoints[point.context] = point
        savedResumePoints += point
    }

    override suspend fun resumePoint(context: PlayContext): ResumePoint? = resumePoints[context]

    override suspend fun resumePointsFor(contexts: List<PlayContext>): Map<PlayContext, ResumePoint> = contexts.mapNotNull { context -> resumePoints[context]?.let { context to it } }.toMap()

    override fun eventCount(): Flow<Int> = eventCount

    override suspend fun clearHistory() {
        plays.clear()
        resumePoints.clear()
    }
}
