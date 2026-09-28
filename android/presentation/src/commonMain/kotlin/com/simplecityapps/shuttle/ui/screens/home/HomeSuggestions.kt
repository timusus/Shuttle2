package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
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
    suspend operator fun invoke(contexts: List<PlayContext>): List<HomeItem> {
        val albums = contexts.filterIsInstance<PlayContext.Album>().map { it.groupKey }
            .takeIf { it.isNotEmpty() }?.let { suggestionsRepository.albums(it) }.orEmpty().associateBy { it.groupKey }
        val artists = contexts.filterIsInstance<PlayContext.AlbumArtist>().map { it.groupKey }
            .takeIf { it.isNotEmpty() }?.let { suggestionsRepository.albumArtists(it) }.orEmpty().associateBy { it.groupKey }
        val genres = contexts.filterIsInstance<PlayContext.Genre>().map { it.name }
            .takeIf { it.isNotEmpty() }?.let { suggestionsRepository.genres(it) }.orEmpty().associateBy { it.name }
        val playlists = if (contexts.any { it is PlayContext.Playlist }) {
            playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).first().associateBy { it.id }
        } else {
            emptyMap()
        }
        return contexts.mapNotNull { context ->
            when (context) {
                is PlayContext.Album -> albums[context.groupKey]?.let { HomeItem.AlbumItem(it) }
                is PlayContext.AlbumArtist -> artists[context.groupKey]?.let { HomeItem.ArtistItem(it) }
                is PlayContext.Genre -> genres[context.name]?.let { HomeItem.GenreItem(it) }
                is PlayContext.Playlist -> playlists[context.playlistId]?.let { HomeItem.PlaylistItem(it) }
                is PlayContext.SmartPlaylist -> HomeItem.SmartPlaylistItem(context.smartPlaylistId)
                is PlayContext.UserSmartPlaylist, PlayContext.None -> null
            }
        }.distinctBy { it.key }
    }
}

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
    suspend operator fun invoke(): JumpBackInCandidates = JumpBackInCandidates(
        fromHistory = resolveHomeItems(playHistoryRepository.recentContexts(CANDIDATES).map { it.context }),
        lastCompleted = resolveHomeItems(suggestionsRepository.recentlyCompletedAlbums(CANDIDATES).map { PlayContext.Album(it) }),
    )

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
        val items = resolveHomeItems(contexts.map { it.context }).associateBy { it.playContext }
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

/** An album or artist played through [completions] times in the window, weighed by age into [score]. */
data class OnRepeatCandidate(
    val item: HomeItem,
    val completions: Int,
    val score: Double,
)

class OnRepeat @Inject constructor(
    private val playHistoryRepository: PlayHistoryRepository,
    private val resolveHomeItems: ResolveHomeItems,
) {
    suspend operator fun invoke(now: Instant): List<OnRepeatCandidate> {
        val since = now - WINDOW_DAYS.days
        val albums = playHistoryRepository.albumCompletions(since, HALF_LIFE_DAYS.days, CANDIDATES)
            .map { Triple(PlayContext.Album(it.groupKey), it.completions, it.score) }
        val artists = playHistoryRepository.albumArtistCompletions(since, HALF_LIFE_DAYS.days, CANDIDATES)
            .map { Triple(PlayContext.AlbumArtist(it.groupKey), it.completions, it.score) }
        val scored = (albums + artists).sortedByDescending { it.third }
        val items = resolveHomeItems(scored.map { it.first }).associateBy { it.playContext }
        return scored.mapNotNull { (context, completions, score) -> items[context]?.let { OnRepeatCandidate(it, completions, score) } }
    }

    companion object {
        const val WINDOW_DAYS = 28
        const val HALF_LIFE_DAYS = 14
        private const val CANDIDATES = 20
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
        return GenrePickCandidates(
            played = played.takeIf { it.isNotEmpty() }?.let { plays -> suggestionsRepository.genres(plays.map { it.genre }) }.orEmpty().map { HomeItem.GenreItem(it) },
            largest = suggestionsRepository.largestGenres(MIN_SONGS, CANDIDATES).map { HomeItem.GenreItem(it) },
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
