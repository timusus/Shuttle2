package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PlayEventDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongGroupCompletionsRow
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumArtistCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumCompletions
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.removeArticles
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
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
    ) {
        if (!song.isInLibrary) return
        val local = startedAt.toLocalDateTime(timeZone())
        playEventDao.insert(
            PlayEventData(
                mediaProvider = song.mediaProvider,
                songPath = song.path,
                startedAt = startedAt,
                listenedMs = listenedMs,
                completed = completed,
                localHour = local.hour,
                weekday = local.dayOfWeek.isoDayNumber,
                contextType = context.type,
                contextId = context.id
            )
        )
        pruneIfDue()
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
        limit: Int
    ): List<AlbumCompletions> = playEventDao.albumCompletions(since, limit)
        .groupBy { row -> AlbumGroupKey(row.album?.lowercase()?.removeArticles(), row.albumArtistGroupKey()) }
        .map { (groupKey, rows) -> AlbumCompletions(groupKey, rows.sumOf { it.completions }, rows.maxOf { it.lastCompletedAt }) }
        .sortedWith(compareByDescending<AlbumCompletions> { it.completions }.thenByDescending { it.lastCompletedAt })

    override suspend fun albumArtistCompletions(
        since: Instant,
        limit: Int
    ): List<AlbumArtistCompletions> = playEventDao.albumArtistCompletions(since, limit)
        .groupBy { row -> row.albumArtistGroupKey() }
        .map { (groupKey, rows) -> AlbumArtistCompletions(groupKey, rows.sumOf { it.completions }, rows.maxOf { it.lastCompletedAt }) }
        .sortedWith(compareByDescending<AlbumArtistCompletions> { it.completions }.thenByDescending { it.lastCompletedAt })

    override suspend fun clearHistory() {
        playEventDao.clear()
    }

    /** As [Song.albumArtistGroupKey] has it. */
    private fun SongGroupCompletionsRow.albumArtistGroupKey() = AlbumArtistGroupKey(
        albumArtist?.lowercase()?.removeArticles()
            ?: artists.joinToString(", ") { it.lowercase().removeArticles() }.ifEmpty { null }
    )

    companion object {
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
