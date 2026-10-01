package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.PlayContext
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The clock and time zone Home's suggestions are computed against; tests pass fixed ones. */
class HomeTime(
    val clock: Clock,
    private val timeZone: () -> TimeZone,
) {
    @Inject
    constructor() : this(Clock.System, { TimeZone.currentSystemDefault() })

    fun timeZone(): TimeZone = timeZone.invoke()
}

/**
 * Turns play contexts into the items Home shows, in the same order, dropping any the library no longer has. Albums and
 * artists are found by the group keys the library repositories build them with, so an item is always one the album and
 * artist screens can open. User smart playlists are dropped: nothing plays them yet.
 */
class ResolveHomeItems @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(contexts: List<PlayContext>): List<HomeItem> = contexts.resolvedIn(byContext(contexts))

    /** The item each of [contexts] resolves to, reading each kind once, for callers that resolve several lists in one go. */
    suspend fun byContext(contexts: List<PlayContext>): Map<PlayContext, HomeItem> {
        val albums = contexts.filterIsInstance<PlayContext.Album>().map { it.groupKey }
            .takeIf { it.isNotEmpty() }?.let { suggestionsRepository.albums(it) }.orEmpty().associateBy { it.groupKey }
        val artists = contexts.filterIsInstance<PlayContext.AlbumArtist>().map { it.groupKey }
            .takeIf { it.isNotEmpty() }?.let { suggestionsRepository.albumArtists(it) }.orEmpty().associateBy { it.groupKey }
        val genres = if (contexts.any { it is PlayContext.Genre }) suggestionsRepository.genres().associateBy { it.name } else emptyMap()
        val playlists = if (contexts.any { it is PlayContext.Playlist }) {
            playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).first().associateBy { it.id }
        } else {
            emptyMap()
        }
        return contexts.distinct().mapNotNull { context ->
            when (context) {
                is PlayContext.Album -> albums[context.groupKey]?.let { HomeItem.AlbumItem(it) }
                is PlayContext.AlbumArtist -> artists[context.groupKey]?.let { HomeItem.ArtistItem(it) }
                is PlayContext.Genre -> genres[context.name]?.let { HomeItem.GenreItem(it) }
                is PlayContext.Playlist -> playlists[context.playlistId]?.let { HomeItem.PlaylistItem(it) }
                is PlayContext.SmartPlaylist -> HomeItem.SmartPlaylistItem(context.smartPlaylistId)
                is PlayContext.UserSmartPlaylist, PlayContext.None -> null
            }?.let { context to it }
        }.toMap()
    }
}

/** The items [items] resolves these contexts to, in order, each once. */
fun List<PlayContext>.resolvedIn(items: Map<PlayContext, HomeItem>): List<HomeItem> = mapNotNull { items[it] }.distinctBy { it.key }

/** Jump back in's candidates: the contexts last played from, and the albums last played through, its fallback. */
data class JumpBackInCandidates(
    val fromHistory: List<HomeItem>,
    val lastCompleted: List<HomeItem>,
)

class JumpBackIn @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val suggestionsRepository: SuggestionsRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    suspend operator fun invoke(): JumpBackInCandidates {
        val fromHistory = playHistoryRepository.recentContexts(CANDIDATES).map { it.context }
        val lastCompleted = suggestionsRepository.recentlyCompletedAlbums(CANDIDATES).map { PlayContext.Album(it) }
        val items = resolveHomeItems.byContext(fromHistory + lastCompleted)
        return JumpBackInCandidates(fromHistory.resolvedIn(items), lastCompleted.resolvedIn(items))
    }

    private companion object {
        const val CANDIDATES = 16
    }
}

/** A context played around this hour on [days] distinct days in the window, [weekendDays] of them at a weekend. */
data class AroundThisTimeCandidate(
    val item: HomeItem,
    val days: Int,
    val weekendDays: Int,
)

class AroundThisTime @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    suspend operator fun invoke(
        now: Instant,
        timeZone: TimeZone,
    ): List<AroundThisTimeCandidate> {
        val hour = now.toLocalDateTime(timeZone).hour
        val contexts = playHistoryRepository.contextsAroundHour(hour, WINDOW_MINUTES, since = now - WINDOW_DAYS.days, limit = CANDIDATES)
        val items = resolveHomeItems.byContext(contexts.map { it.context })
        return contexts.mapNotNull { context ->
            items[context.context]?.let { AroundThisTimeCandidate(it, context.days, context.weekendDays) }
        }
    }

    companion object {
        const val WINDOW_MINUTES = 90
        const val WINDOW_DAYS = 60
        private const val CANDIDATES = 30
    }
}

/** An album or artist listened to on [days] distinct days in the window, the last time at [lastPlayedAt]. */
data class HeavyRotationCandidate(
    val item: HomeItem,
    val days: Int,
    val lastPlayedAt: Instant,
)

/**
 * The albums and artists listened to on the most distinct days in the last [WINDOW_DAYS] (#671), so one sitting with a
 * long album counts once. A day counts for an album when [MIN_SONGS_A_DAY] of its songs were played through, or half of
 * an album of fewer than [SHORT_ALBUM_TRACKS] tracks; for an artist, when that many of their songs were, or half of one
 * of their short albums. Most days first, then the most recently played.
 */
class HeavyRotation @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    private class Tally(
        val context: PlayContext,
        val days: Int,
        val lastPlayedAt: Instant,
    )

    suspend operator fun invoke(now: Instant): List<HeavyRotationCandidate> {
        val albumDays = playHistoryRepository.albumDays(since = now - WINDOW_DAYS.days)
        val albums = albumDays.groupBy { it.groupKey }.map { (key, days) ->
            Tally(PlayContext.Album(key), days.count { it.counts }, days.maxOf { it.lastCompletedAt })
        }
        val artists = albumDays.groupBy { it.albumArtistGroupKey }.map { (key, albums) ->
            val days = albums.groupBy { it.day }.values.count { day -> day.sumOf { it.songs } >= MIN_SONGS_A_DAY || day.any { it.counts } }
            Tally(PlayContext.AlbumArtist(key), days, albums.maxOf { it.lastCompletedAt })
        }
        val tallies = (albums + artists)
            .filter { it.days > 0 }
            .sortedWith(compareByDescending<Tally> { it.days }.thenByDescending { it.lastPlayedAt })
            .take(CANDIDATES)
        val items = resolveHomeItems.byContext(tallies.map { it.context })
        return tallies.mapNotNull { tally -> items[tally.context]?.let { HeavyRotationCandidate(it, tally.days, tally.lastPlayedAt) } }
    }

    /** Whether this day's listening to the album counts as a day of it. */
    private val AlbumDay.counts: Boolean
        get() = songs >= MIN_SONGS_A_DAY || (trackCount < SHORT_ALBUM_TRACKS && songs * 2 >= trackCount)

    companion object {
        const val WINDOW_DAYS = 28
        const val MIN_SONGS_A_DAY = 3
        const val SHORT_ALBUM_TRACKS = 6
        private const val CANDIDATES = 40
    }
}

/** Albums played at least [MIN_PLAYS] times, or holding a favourite, and not played for [UNPLAYED_DAYS] days. */
class Rediscover @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    suspend operator fun invoke(now: Instant): List<HomeItem> = resolveHomeItems(
        suggestionsRepository.albumsToRediscover(MIN_PLAYS, playedBefore = now - UNPLAYED_DAYS.days, limit = CANDIDATES).map { PlayContext.Album(it) },
    )

    companion object {
        const val MIN_PLAYS = 3
        const val UNPLAYED_DAYS = 90
        private const val CANDIDATES = 60
    }
}

/** The library's newest albums by date added, newest first (#649). */
class RecentlyAdded @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    suspend operator fun invoke(): List<HomeItem> = resolveHomeItems(suggestionsRepository.recentlyAddedAlbums(CANDIDATES).map { PlayContext.Album(it) })

    private companion object {
        const val CANDIDATES = 30
    }
}

/** The genres played in the window, most by age-weighed plays first, and the library's largest genres to fill in. */
data class GenrePickCandidates(
    val played: List<HomeItem>,
    val largest: List<HomeItem>,
)

class GenrePicks @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val suggestionsRepository: SuggestionsRepository,
) {
    suspend operator fun invoke(now: Instant): GenrePickCandidates {
        val played = playHistoryRepository.genrePlays(since = now - WINDOW_DAYS.days, halfLife = HALF_LIFE_DAYS.days, limit = CANDIDATES)
        val genres = suggestionsRepository.genres()
        val byName = genres.associateBy { it.name }
        return GenrePickCandidates(
            played = played.map { it.genre }.distinct().mapNotNull { byName[it] }.map { HomeItem.GenreItem(it) },
            largest = genres
                .filter { it.songCount >= MIN_SONGS }
                .sortedWith(compareByDescending<Genre> { it.songCount }.thenBy { it.name })
                .take(CANDIDATES)
                .map { HomeItem.GenreItem(it) },
        )
    }

    companion object {
        const val WINDOW_DAYS = 90
        const val HALF_LIFE_DAYS = 14

        /** A genre needs this many songs to be picked by size alone. */
        const val MIN_SONGS = 20
        private const val CANDIDATES = 20
    }
}
