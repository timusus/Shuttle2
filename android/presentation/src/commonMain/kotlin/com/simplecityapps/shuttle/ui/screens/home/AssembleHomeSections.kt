package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.ui.text.StringKey
import dev.zacsweers.metro.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Everything Home's sections are chosen from; [hasHistory] is whether the listening history holds any event. A section's
 * candidates are null while they're still loading.
 */
data class HomeCandidates(
    val hasHistory: Boolean,
    val jumpBackIn: JumpBackInCandidates? = null,
    val aroundThisTime: List<AroundThisTimeCandidate>? = null,
    val heavyRotation: List<HeavyRotationCandidate>? = null,
    val rediscover: List<HomeItem>? = null,
    val recentlyAdded: List<HomeItem>? = null,
    val genrePicks: GenrePickCandidates? = null,
) {
    /** Whether every section's candidates have loaded. */
    val complete: Boolean
        get() = jumpBackIn != null && aroundThisTime != null && heavyRotation != null && rediscover != null && recentlyAdded != null && genrePicks != null
}

/**
 * Chooses Home's sections from [candidates] as of [at] in [timeZone] (#633). While some candidates are still loading, it
 * stops at the first section whose candidates (or the cold start's choice) wait on them: the sections it gives are the
 * leading ones of the full set, each as it will be once everything has loaded, as a section only depends on the ones before it. Sections come in [HomeSectionId]
 * order and an item shows once, in the earliest section that shows it; a hidden section claims nothing. Each section's
 * thresholds are checked on its own candidates, and a section hides when fewer than [MIN_SHOWN_ITEMS] are left to show.
 * Every section but Shuffle all carries a one-line subtitle saying what it is (#671).
 * - Cold start (no history, nothing ever played through): Recently added, Genre picks by size, and Shuffle all.
 * - Jump back in: the last [JUMP_BACK_IN_SIZE] contexts played from; the albums last played through until there's history.
 * - Around this time: contexts by days played near this hour, days of today's kind (weekday or weekend) counting
 *   [SAME_KIND_OF_DAY_WEIGHT]x; shown when [AROUND_THIS_TIME_MIN_ITEMS] contexts were each played on [AROUND_THIS_TIME_MIN_DAYS]
 *   days. Titled for the part of the day.
 * - Heavy rotation: albums and artists listened to on [HEAVY_ROTATION_MIN_DAYS] days or more (see [HeavyRotation]).
 * - Rediscover: shuffled with a seed that changes each local day, so it holds still through the day.
 * - Recently added: the library's newest albums, on a first import too, when everything was added at once (#649).
 * - Genre picks: played genres first, then the largest; [GENRE_PICKS_MIN] to [GENRE_PICKS_MAX] of them, else hidden. The
 *   ones shown are ordered by a hash seeded with the local day, so a play doesn't reshuffle them within the day (#672).
 */
fun assembleHomeSections(
    candidates: HomeCandidates,
    at: Instant,
    timeZone: TimeZone,
): List<HomeSection> {
    val now = at.toLocalDateTime(timeZone)
    val day = now.date.toEpochDays()
    val builder = SectionBuilder()
    val jumpBackInCandidates = candidates.jumpBackIn ?: return builder.sections
    val coldStart = !candidates.hasHistory && jumpBackInCandidates.lastCompleted.isEmpty()
    if (coldStart) {
        builder.add(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, StringKey.HOME_RECENTLY_ADDED_SUBTITLE, candidates.recentlyAdded ?: return builder.sections, SECTION_SIZE)
        val largest = candidates.genrePicks?.largest ?: return builder.sections
        builder.add(
            HomeSectionId.GenrePicks,
            HomeSectionTitle.GenrePicks,
            StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE,
            largest,
            GENRE_PICKS_MAX,
            eligible = largest.size >= GENRE_PICKS_MIN,
            arrange = { it.dailyOrder(day) },
        )
        return builder.sections + HomeSection(HomeSectionId.ShuffleAll, HomeSectionTitle.ShuffleAll, subtitle = null, items = emptyList())
    }

    val jumpBackIn = jumpBackInCandidates.fromHistory.ifEmpty { jumpBackInCandidates.lastCompleted }
    builder.add(HomeSectionId.JumpBackIn, HomeSectionTitle.JumpBackIn, StringKey.HOME_JUMP_BACK_IN_SUBTITLE, jumpBackIn, JUMP_BACK_IN_SIZE)

    val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
    val aroundThisTime = (candidates.aroundThisTime ?: return builder.sections)
        .filter { it.days >= AROUND_THIS_TIME_MIN_DAYS }
        .sortedByDescending { candidate ->
            val sameKind = if (weekend) candidate.weekendDays else candidate.days - candidate.weekendDays
            sameKind * SAME_KIND_OF_DAY_WEIGHT + (candidate.days - sameKind)
        }
        .map { it.item }
    builder.add(
        HomeSectionId.AroundThisTime,
        partOfDay(now.hour),
        StringKey.HOME_AROUND_THIS_TIME_SUBTITLE,
        aroundThisTime,
        SECTION_SIZE,
        eligible = aroundThisTime.size >= AROUND_THIS_TIME_MIN_ITEMS,
    )

    val heavyRotation = (candidates.heavyRotation ?: return builder.sections).filter { it.days >= HEAVY_ROTATION_MIN_DAYS }.map { it.item }
    builder.add(HomeSectionId.HeavyRotation, HomeSectionTitle.HeavyRotation, StringKey.HOME_HEAVY_ROTATION_SUBTITLE, heavyRotation, SECTION_SIZE)

    val rediscover = (candidates.rediscover ?: return builder.sections).shuffled(Random(day))
    builder.add(HomeSectionId.Rediscover, HomeSectionTitle.Rediscover, StringKey.HOME_REDISCOVER_SUBTITLE, rediscover, SECTION_SIZE)

    builder.add(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, StringKey.HOME_RECENTLY_ADDED_SUBTITLE, candidates.recentlyAdded ?: return builder.sections, SECTION_SIZE)

    val genrePickCandidates = candidates.genrePicks ?: return builder.sections
    val played = genrePickCandidates.played.map { it.key }.toSet()
    val genrePicks = (genrePickCandidates.played + genrePickCandidates.largest).distinctBy { it.key }
    builder.add(
        HomeSectionId.GenrePicks,
        HomeSectionTitle.GenrePicks,
        subtitle = { shown -> if (shown.any { it.key in played }) StringKey.HOME_GENRE_PICKS_SUBTITLE else StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE },
        genrePicks,
        GENRE_PICKS_MAX,
        eligible = genrePicks.size >= GENRE_PICKS_MIN,
        arrange = { it.dailyOrder(day) },
    )
    return builder.sections
}

/** [this] in an order that holds for the local [day] and changes with it; an item keeps its place among the others as they come and go. */
private fun List<HomeItem>.dailyOrder(day: Long): List<HomeItem> = sortedBy { "$day|${it.key}".hashCode() }

/** Morning from 4am, afternoon from noon, night from 6pm. */
internal fun partOfDay(hour: Int): HomeSectionTitle = when (hour) {
    in 4..11 -> HomeSectionTitle.ThisMorning
    in 12..17 -> HomeSectionTitle.ThisAfternoon
    else -> HomeSectionTitle.Tonight
}

/**
 * Adds sections in order, leaving out items an earlier section shows. Whether a section qualifies ([eligible]) is decided
 * on its own candidates, before that; afterwards it hides only when fewer than [MIN_SHOWN_ITEMS] items are left.
 */
private class SectionBuilder {
    private val shown = mutableSetOf<String>()
    val sections = mutableListOf<HomeSection>()

    fun add(
        id: HomeSectionId,
        title: HomeSectionTitle,
        subtitle: StringKey,
        candidates: List<HomeItem>,
        size: Int,
        eligible: Boolean = candidates.isNotEmpty(),
        arrange: (List<HomeItem>) -> List<HomeItem> = { it },
    ) = add(id, title, { subtitle }, candidates, size, eligible, arrange)

    /** As above, the subtitle chosen for the items shown; [arrange] orders them once chosen. */
    fun add(
        id: HomeSectionId,
        title: HomeSectionTitle,
        subtitle: (shown: List<HomeItem>) -> StringKey,
        candidates: List<HomeItem>,
        size: Int,
        eligible: Boolean = candidates.isNotEmpty(),
        arrange: (List<HomeItem>) -> List<HomeItem> = { it },
    ) {
        if (!eligible) return
        val items = candidates.filter { it.key !in shown }.distinctBy { it.key }.take(size)
        if (items.size < MIN_SHOWN_ITEMS) return
        shown += items.map { it.key }
        sections += HomeSection(id, title, subtitle(items), arrange(items))
    }
}

internal const val JUMP_BACK_IN_SIZE = 8
internal const val SECTION_SIZE = 10

/** The fewest items a section shows once earlier sections have claimed theirs; below it, the section hides. */
internal const val MIN_SHOWN_ITEMS = 2
internal const val AROUND_THIS_TIME_MIN_DAYS = 3
internal const val AROUND_THIS_TIME_MIN_ITEMS = 3
internal const val SAME_KIND_OF_DAY_WEIGHT = 1.5
internal const val HEAVY_ROTATION_MIN_DAYS = 3
internal const val GENRE_PICKS_MIN = 4
internal const val GENRE_PICKS_MAX = 6

/**
 * Loads every section's candidates, side by side, and assembles Home's sections from them as they come (#688): each
 * emission is the leading sections whose candidates, and those of every section before them, have loaded, so a section
 * appears once and never changes after; the last is all of them. A partial set with no section yet isn't emitted. Jump
 * back in's items carry where each one's queue was left (#670), finished or not (#706): none for an item never played from.
 */
class LoadHomeSections @Inject constructor(
    private val jumpBackIn: JumpBackIn,
    private val aroundThisTime: AroundThisTime,
    private val heavyRotation: HeavyRotation,
    private val rediscover: Rediscover,
    private val recentlyAdded: RecentlyAdded,
    private val genrePicks: GenrePicks,
    private val playHistoryRepository: PlayHistoryRepository,
    private val homeTime: HomeTime,
) {
    operator fun invoke(hasHistory: Boolean): Flow<List<HomeSection>> = channelFlow {
        val started = TimeSource.Monotonic.markNow()
        val now = homeTime.clock.now()
        val timeZone = homeTime.timeZone()
        val candidates = MutableStateFlow(HomeCandidates(hasHistory))
        val reads = HomeReads()

        fun <T> load(
            stage: String,
            block: suspend () -> T,
            set: HomeCandidates.(T) -> HomeCandidates,
        ) = launch {
            val loaded = timed(stage) { block() }
            candidates.update { it.set(loaded) }
        }
        load("Jump back in", { jumpBackIn(reads) }) { copy(jumpBackIn = it) }
        load("Around this time", { if (hasHistory) aroundThisTime(now, timeZone, reads) else emptyList() }) { copy(aroundThisTime = it) }
        load("Heavy rotation", { if (hasHistory) heavyRotation(now, reads) else emptyList() }) { copy(heavyRotation = it) }
        load("Rediscover", { rediscover(now, reads) }) { copy(rediscover = it) }
        load("Recently added", { recentlyAdded(reads) }) { copy(recentlyAdded = it) }
        load("Genre picks", { genrePicks(now, reads) }) { copy(genrePicks = it) }

        var jumpBackInProgress: Map<String, HomeItemProgress>? = null
        var last: List<HomeSection>? = null
        candidates.transformWhile { loaded ->
            val sections = assembleHomeSections(loaded, now, timeZone).map { section ->
                if (section.id == HomeSectionId.JumpBackIn) {
                    section.copy(progress = jumpBackInProgress ?: timed("Resume points") { progress(section.items) }.also { jumpBackInProgress = it })
                } else {
                    section
                }
            }
            if (sections != last && (sections.isNotEmpty() || loaded.complete)) {
                if (last == null) logger.info { "Home: first sections in ${started.elapsedNow()}" }
                last = sections
                emit(sections)
            }
            !loaded.complete
        }.collect { send(it) }
        logger.info { "Home: sections loaded in ${started.elapsedNow()}" }
    }

    /** Runs [block], logging how long the stage of the load it is took. */
    private suspend fun <T> timed(
        stage: String,
        block: suspend () -> T,
    ): T {
        val started = TimeSource.Monotonic.markNow()
        return block().also { logger.info { "Home: $stage loaded in ${started.elapsedNow()}" } }
    }

    private suspend fun progress(items: List<HomeItem>): Map<String, HomeItemProgress> {
        val points = playHistoryRepository.resumePointsFor(items.map { it.playContext })
        return items.mapNotNull { item -> points[item.playContext]?.let { item.key to HomeItemProgress.of(it) } }.toMap()
    }

    private companion object {
        /** In Release too: the cold-start measurements read Home's load (docs/performance/ios-startup.md). */
        val logger = Logger.tagged("Startup")
    }
}

/**
 * Home's sections, or null while the library is empty. They're loaded at set moments only, never per play, so nothing
 * moves under the user's thumb (#672): each time Home becomes [visible] (it appears, the user returns to its tab, the app
 * comes to the foreground), and while it's visible on each of [refreshes] (pull to refresh), when an import completes
 * and when the library turns empty or stops being so. While it's hidden they reload as the local hour turns, for Around
 * this time's window and the daily seeds, so returning finds them fresh. Nothing loads until Home is first visible.
 * Until sections have shown, they come in as each loads (see [LoadHomeSections]); after that, a reload replaces them all
 * at once, so the ones on screen don't shrink back and grow again.
 */
class ObserveHomeSections @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val songImportStateProvider: SongImportStateProvider,
    private val loadHomeSections: LoadHomeSections,
    private val homeTime: HomeTime,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        visible: Flow<Boolean>,
        refreshes: Flow<Unit>,
    ): Flow<List<HomeSection>?> = flow {
        var shown = false
        val loads = visible.distinctUntilChanged()
            .flatMapLatest { visible -> if (visible) merge(flowOf(Unit), refreshes, importsCompleted(), libraryFilledOrEmptied()) else hourTurns() }
            .transformLatest {
                val counting = TimeSource.Monotonic.markNow()
                val songCount = suggestionsRepository.songCount().first()
                logger.info { "Home: songCount in ${counting.elapsedNow()}" }
                if (songCount == 0) {
                    shown = false
                    emit(null)
                } else {
                    val countingEvents = TimeSource.Monotonic.markNow()
                    val hasHistory = playHistoryRepository.eventCount().first() > 0
                    logger.info { "Home: eventCount in ${countingEvents.elapsedNow()}" }
                    val sections = loadHomeSections(hasHistory = hasHistory)
                    if (shown) emit(sections.last()) else emitAll(sections.onEach { shown = true })
                }
            }
        emitAll(loads)
    }.flowOn(dispatcher)

    private fun importsCompleted(): Flow<Unit> = songImportStateProvider.importsCompleted.drop(1).map { }

    private fun libraryFilledOrEmptied(): Flow<Unit> = suggestionsRepository.songCount().map { it > 0 }.distinctUntilChanged().drop(1).map { }

    /** Each turn of the local hour from now, not the current one. */
    private fun hourTurns(): Flow<Unit> = flow {
        while (true) {
            val now = homeTime.clock.now().toLocalDateTime(homeTime.timeZone())
            emit(now.date.toEpochDays() to now.hour)
            delay((60 - now.minute).times(60).seconds - now.second.seconds)
        }
    }.distinctUntilChanged().drop(1).map { }

    private companion object {
        val logger = Logger.tagged("Startup")
    }
}
