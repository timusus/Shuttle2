package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.ResumePointDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime

/**
 * [PlayHistoryRepository] over `play_events`, and `resume_points` for where each context was left. Each recorded play prunes the history back to
 * [PlayHistoryRepository.RETENTION_DAYS] and [PlayHistoryRepository.MAX_EVENTS], at most once a day.
 */
class LocalPlayHistoryRepository(
    private val playEventDao: PlayEventDao,
    private val resumePointDao: ResumePointDao,
    private val albumIndex: AlbumIndexProvider,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = TimeZone::currentSystemDefault
) : PlayHistoryRepository {
    private var lastPrunedAt: Instant? = null

    override suspend fun recordPlay(
        song: Song,
        startedAt: Instant,
        listenedMs: Long,
        completed: Boolean,
        context: PlayContext
    ): Long? {
        if (!song.isInLibrary) return null
        val playedFrom = current(context)
        val local = startedAt.toLocalDateTime(timeZone())
        val id = playEventDao.insert(
            PlayEventData(
                mediaProvider = song.mediaProvider,
                songPath = song.path,
                startedAt = startedAt,
                listenedMs = listenedMs,
                completed = completed,
                localHour = local.hour,
                weekday = local.dayOfWeek.isoDayNumber,
                contextType = playedFrom.type,
                contextId = playedFrom.id
            )
        )
        pruneIfDue()
        return id
    }

    override suspend fun completePlay(
        id: Long,
        listenedMs: Long
    ) {
        playEventDao.complete(id, listenedMs)
    }

    /**
     * [context] as it names its album or album artist now: a queue saved before the album identity rule (#637) still
     * holds the old key, so it's moved as the stored history was ([AlbumKeyMigration]); kept as it is when no song has it.
     */
    private suspend fun current(context: PlayContext): PlayContext {
        if (context !is PlayContext.Album && context !is PlayContext.AlbumArtist) return context
        return albumIndex.albumIndex().rekey.context(context) ?: context
    }

    private suspend fun pruneIfDue() {
        val now = clock.now()
        val last = lastPrunedAt
        if (last != null && now - last < 1.days) return
        lastPrunedAt = now
        playEventDao.prune(before = now - PlayHistoryRepository.RETENTION_DAYS.days, keep = PlayHistoryRepository.MAX_EVENTS)
    }

    override suspend fun recentContexts(limit: Int): List<RecentContext> = playEventDao.recentContexts(limit).map { row ->
        RecentContext(PlayContext.decode(row.contextType, row.contextId), row.lastPlayedAt)
    }

    override suspend fun contextsAroundHour(
        hour: Int,
        windowMinutes: Int,
        since: Instant,
        limit: Int
    ): List<ContextDays> = playEventDao.contextsInHours(hoursAround(hour, windowMinutes), since, utcOffsetMs(), limit).map { row ->
        ContextDays(PlayContext.decode(row.contextType, row.contextId), row.days, row.weekendDays, row.lastPlayedAt)
    }

    override suspend fun albumDays(since: Instant): List<AlbumDay> {
        val rows = playEventDao.completedSongDays(since, utcOffsetMs())
        if (rows.isEmpty()) return emptyList()
        val index = albumIndex.albumIndex()
        return rows
            .mapNotNull { row -> index.identities[row.songId]?.let { identity -> identity.groupKey to row } }
            .groupBy({ (groupKey, row) -> groupKey to row.day }, { (_, row) -> row })
            .map { (albumDay, rows) ->
                val (groupKey, day) = albumDay
                AlbumDay(groupKey, day, songs = rows.size, trackCount = index.songIds(groupKey).size, lastCompletedAt = rows.maxOf { it.lastPlayedAt })
            }
    }

    override suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays> {
        val utcOffsetMs = utcOffsetMs()
        val today = (clock.now().toEpochMilliseconds() + utcOffsetMs) / DAY_MS
        val totals = mutableMapOf<String, Pair<Int, Double>>()
        playEventDao.playsByGenresAndDay(since, utcOffsetMs).forEach { row ->
            val weight = row.plays * decay(today - row.day, halfLife)
            row.genres.filter { it.isNotBlank() }.distinct().forEach { genre ->
                val (plays, score) = totals[genre] ?: (0 to 0.0)
                totals[genre] = (plays + row.plays) to (score + weight)
            }
        }
        return totals
            .map { (genre, total) -> GenrePlays(genre, total.first, total.second) }
            .sortedWith(compareByDescending<GenrePlays> { it.score }.thenBy { it.genre })
            .take(limit)
    }

    /**
     * The local time zone's offset from UTC now, which days and hours are counted in. One offset for the whole window,
     * so a play near midnight before a daylight saving change can land a day off; close enough for ranking.
     */
    private fun utcOffsetMs(): Long = timeZone().offsetAt(clock.now()).totalSeconds * 1000L

    override suspend fun saveResumePoint(point: ResumePoint) {
        val context = current(point.context)
        val contextId = context.id ?: return
        resumePointDao.upsert(
            ResumePointData(
                contextType = context.type,
                contextId = contextId,
                mediaProvider = point.mediaProvider,
                songPath = point.songPath,
                positionMs = point.positionMs,
                track = point.track,
                trackCount = point.trackCount,
                shuffled = point.shuffled,
                finished = point.finished,
                updatedAt = point.updatedAt
            )
        )
    }

    override suspend fun resumePoint(context: PlayContext): ResumePoint? {
        val current = current(context)
        val contextId = current.id ?: return null
        return resumePointDao.get(current.type, contextId)?.let { row ->
            ResumePoint(
                context = context,
                mediaProvider = row.mediaProvider,
                songPath = row.songPath,
                positionMs = row.positionMs,
                track = row.track,
                trackCount = row.trackCount,
                shuffled = row.shuffled,
                finished = row.finished,
                updatedAt = row.updatedAt
            )
        }
    }

    override fun eventCount(): Flow<Int> = playEventDao.observeCount()

    override suspend fun clearHistory() {
        playEventDao.clear()
        resumePointDao.clear()
    }

    /** A play [ageDays] old's weight: 1 today, halving every [halfLife]. */
    private fun decay(
        ageDays: Long,
        halfLife: Duration
    ): Double = 2.0.pow(-ageDays.coerceAtLeast(0) / halfLife.toDouble(DurationUnit.DAYS))

    companion object {
        private const val DAY_MS = 86_400_000L

        /** The local hours (0 to 23) that the [windowMinutes] either side of [hour]:00 touch, wrapping round midnight. */
        internal fun hoursAround(
            hour: Int,
            windowMinutes: Int
        ): List<Int> {
            val center = hour * 60
            val first = (center - windowMinutes).floorDiv(60)
            val last = maxOf(center, center + windowMinutes - 1).floorDiv(60)
            return (first..last).map { it.mod(24) }.distinct().sorted()
        }
    }
}
