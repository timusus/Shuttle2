package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumArtistCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.shuttle.model.AlbumIdentity
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
 * [PlayHistoryRepository] over `play_events`. Each recorded play prunes the history back to
 * [PlayHistoryRepository.RETENTION_DAYS] and [PlayHistoryRepository.MAX_EVENTS], at most once a day.
 */
class LocalPlayHistoryRepository(
    private val playEventDao: PlayEventDao,
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
    ): List<ContextDays> {
        val utcOffsetMs = timeZone().offsetAt(clock.now()).totalSeconds * 1000L
        return playEventDao.contextsInHours(hoursAround(hour, windowMinutes), since, utcOffsetMs, limit).map { row ->
            ContextDays(PlayContext.decode(row.contextType, row.contextId), row.days, row.weekendDays, row.lastPlayedAt)
        }
    }

    override suspend fun albumCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumCompletions> = scoredCompletions(since, halfLife) { identity -> identity.groupKey }
        .map { (key, total) -> AlbumCompletions(key, total.plays, total.score, total.lastPlayedAt) }
        .sortedWith(compareByDescending<AlbumCompletions> { it.score }.thenByDescending { it.lastCompletedAt })
        .take(limit)

    override suspend fun albumArtistCompletions(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<AlbumArtistCompletions> = scoredCompletions(since, halfLife) { identity -> identity.albumArtistGroupKey }
        .map { (key, total) -> AlbumArtistCompletions(key, total.plays, total.score, total.lastPlayedAt) }
        .sortedWith(compareByDescending<AlbumArtistCompletions> { it.score }.thenByDescending { it.lastCompletedAt })
        .take(limit)

    override suspend fun genrePlays(
        since: Instant,
        halfLife: Duration,
        limit: Int
    ): List<GenrePlays> {
        val today = clock.now().toEpochMilliseconds() / DAY_MS
        val totals = mutableMapOf<String, Pair<Int, Double>>()
        playEventDao.playsByGenresAndDay(since, MAX_DAY_ROWS).forEach { row ->
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

    override fun eventCount(): Flow<Int> = playEventDao.observeCount()

    override suspend fun clearHistory() {
        playEventDao.clear()
    }

    private class ScoredTotal(
        val plays: Int,
        val score: Double,
        val lastPlayedAt: Instant
    )

    /**
     * The plays through since [since], merged by [key] of each song's album identity (the library's, as the album and
     * artist repositories group by it, so each result names an album or artist they have), each day's plays weighed by
     * their age. A song whose identity isn't known (removed since) is left out.
     */
    private suspend fun <K> scoredCompletions(
        since: Instant,
        halfLife: Duration,
        key: (AlbumIdentity) -> K
    ): List<Pair<K, ScoredTotal>> {
        val today = clock.now().toEpochMilliseconds() / DAY_MS
        val rows = playEventDao.completionsBySongAndDay(since, MAX_DAY_ROWS)
        if (rows.isEmpty()) return emptyList()
        val identities = albumIndex.albumIndex().identities
        return rows
            .mapNotNull { row -> identities[row.songId]?.let { identity -> key(identity) to row } }
            .groupBy({ it.first }, { it.second })
            .map { (groupKey, rows) ->
                groupKey to ScoredTotal(
                    plays = rows.sumOf { it.plays },
                    score = rows.sumOf { it.plays * decay(today - it.day, halfLife) },
                    lastPlayedAt = rows.maxOf { it.lastPlayedAt }
                )
            }
    }

    /** A play [ageDays] old's weight: 1 today, halving every [halfLife]. */
    private fun decay(
        ageDays: Long,
        halfLife: Duration
    ): Double = 2.0.pow(-ageDays.coerceAtLeast(0) / halfLife.toDouble(DurationUnit.DAYS))

    companion object {
        private const val DAY_MS = 86_400_000L

        /** A cap on the (song, day) rows an aggregate reads, far above a year of anyone's listening. */
        private const val MAX_DAY_ROWS = 20_000

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
