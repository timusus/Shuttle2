package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.suggestions.SuggestionsRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import dev.zacsweers.metro.Inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.withIndex
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Everything Home's sections are chosen from; [hasHistory] is whether the listening history holds any event. */
data class HomeCandidates(
    val hasHistory: Boolean,
    val jumpBackIn: JumpBackInCandidates,
    val aroundThisTime: List<AroundThisTimeCandidate>,
    val onRepeat: List<OnRepeatCandidate>,
    val rediscover: List<HomeItem>,
    val recentlyAdded: RecentlyAddedCandidates,
    val genrePicks: GenrePickCandidates,
)

/**
 * Chooses Home's sections from [candidates] as of [clock]'s now in [timeZone] (#633). Sections come in [HomeSectionId]
 * order and an item shows once, in the earliest section that shows it; a hidden section claims nothing. Each section's
 * thresholds are checked on its own candidates, and a section hides when fewer than [MIN_SHOWN_ITEMS] are left to show.
 * - Cold start (no history, nothing ever played through): Recently added, Genre picks by size, and Shuffle all.
 * - Jump back in: the last [JUMP_BACK_IN_SIZE] contexts played from; the albums last played through until there's history.
 * - Around this time: contexts by days played near this hour, days of today's kind (weekday or weekend) counting
 *   [SAME_KIND_OF_DAY_WEIGHT]x; shown when [AROUND_THIS_TIME_MIN_ITEMS] contexts were each played on [AROUND_THIS_TIME_MIN_DAYS]
 *   days. Titled for the part of the day.
 * - On repeat: albums and artists played through [ON_REPEAT_MIN_COMPLETIONS] times, by age-weighed completions.
 * - Rediscover: shuffled with a seed that changes each local day, so it holds still through the day.
 * - Recently added: hidden when more than [IMPORT_BURST_SHARE] of the library was added on one day, the first import.
 * - Genre picks: played genres first, then the largest; [GENRE_PICKS_MIN] to [GENRE_PICKS_MAX] of them, else hidden.
 */
fun assembleHomeSections(
    candidates: HomeCandidates,
    clock: Clock,
    timeZone: TimeZone,
): List<HomeSection> {
    val now = clock.now().toLocalDateTime(timeZone)
    val builder = SectionBuilder()
    val recentlyAdded = candidates.recentlyAdded.items.takeUnless { candidates.recentlyAdded.isImportBurst() }.orEmpty()
    val coldStart = !candidates.hasHistory && candidates.jumpBackIn.lastCompleted.isEmpty()
    if (coldStart) {
        builder.add(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, recentlyAdded, SECTION_SIZE)
        val largest = candidates.genrePicks.largest
        builder.add(HomeSectionId.GenrePicks, HomeSectionTitle.GenrePicks, largest, GENRE_PICKS_MAX, eligible = largest.size >= GENRE_PICKS_MIN)
        return builder.sections + HomeSection(HomeSectionId.ShuffleAll, HomeSectionTitle.ShuffleAll, emptyList())
    }

    val jumpBackIn = candidates.jumpBackIn.fromHistory.ifEmpty { candidates.jumpBackIn.lastCompleted }
    builder.add(HomeSectionId.JumpBackIn, HomeSectionTitle.JumpBackIn, jumpBackIn, JUMP_BACK_IN_SIZE)

    val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
    val aroundThisTime = candidates.aroundThisTime
        .filter { it.days >= AROUND_THIS_TIME_MIN_DAYS }
        .sortedByDescending { candidate ->
            val sameKind = if (weekend) candidate.weekendDays else candidate.days - candidate.weekendDays
            sameKind * SAME_KIND_OF_DAY_WEIGHT + (candidate.days - sameKind)
        }
        .map { it.item }
    builder.add(HomeSectionId.AroundThisTime, partOfDay(now.hour), aroundThisTime, SECTION_SIZE, eligible = aroundThisTime.size >= AROUND_THIS_TIME_MIN_ITEMS)

    val onRepeat = candidates.onRepeat.filter { it.completions >= ON_REPEAT_MIN_COMPLETIONS }.sortedByDescending { it.score }.map { it.item }
    builder.add(HomeSectionId.OnRepeat, HomeSectionTitle.OnRepeat, onRepeat, SECTION_SIZE)

    val rediscover = candidates.rediscover.shuffled(Random(now.date.toEpochDays()))
    builder.add(HomeSectionId.Rediscover, HomeSectionTitle.Rediscover, rediscover, SECTION_SIZE)

    builder.add(HomeSectionId.RecentlyAdded, HomeSectionTitle.RecentlyAdded, recentlyAdded, SECTION_SIZE)

    val genrePicks = (candidates.genrePicks.played + candidates.genrePicks.largest).distinctBy { it.key }
    builder.add(HomeSectionId.GenrePicks, HomeSectionTitle.GenrePicks, genrePicks, GENRE_PICKS_MAX, eligible = genrePicks.size >= GENRE_PICKS_MIN)
    return builder.sections
}

/** Morning from 4am, afternoon from noon, night from 6pm. */
internal fun partOfDay(hour: Int): HomeSectionTitle = when (hour) {
    in 4..11 -> HomeSectionTitle.ThisMorning
    in 12..17 -> HomeSectionTitle.ThisAfternoon
    else -> HomeSectionTitle.Tonight
}

private fun RecentlyAddedCandidates.isImportBurst(): Boolean = importDays.songs > 0 && importDays.largestDay > importDays.songs * IMPORT_BURST_SHARE

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
        candidates: List<HomeItem>,
        size: Int,
        eligible: Boolean = candidates.isNotEmpty(),
    ) {
        if (!eligible) return
        val items = candidates.filter { it.key !in shown }.distinctBy { it.key }.take(size)
        if (items.size < MIN_SHOWN_ITEMS) return
        shown += items.map { it.key }
        sections += HomeSection(id, title, items)
    }
}

internal const val JUMP_BACK_IN_SIZE = 8
internal const val SECTION_SIZE = 10

/** The fewest items a section shows once earlier sections have claimed theirs; below it, the section hides. */
internal const val MIN_SHOWN_ITEMS = 2
internal const val AROUND_THIS_TIME_MIN_DAYS = 3
internal const val AROUND_THIS_TIME_MIN_ITEMS = 3
internal const val SAME_KIND_OF_DAY_WEIGHT = 1.5
internal const val ON_REPEAT_MIN_COMPLETIONS = 5
internal const val IMPORT_BURST_SHARE = 0.8
internal const val GENRE_PICKS_MIN = 4
internal const val GENRE_PICKS_MAX = 6

/** Loads every section's candidates and assembles Home's sections from them. */
class LoadHomeSections @Inject constructor(
    private val jumpBackIn: JumpBackIn,
    private val aroundThisTime: AroundThisTime,
    private val onRepeat: OnRepeat,
    private val rediscover: Rediscover,
    private val recentlyAdded: RecentlyAdded,
    private val genrePicks: GenrePicks,
    private val homeTime: HomeTime,
) {
    suspend operator fun invoke(hasHistory: Boolean): List<HomeSection> {
        val now = homeTime.clock.now()
        val timeZone = homeTime.timeZone()
        val candidates = HomeCandidates(
            hasHistory = hasHistory,
            jumpBackIn = jumpBackIn(),
            aroundThisTime = if (hasHistory) aroundThisTime(now, timeZone) else emptyList(),
            onRepeat = if (hasHistory) onRepeat(now) else emptyList(),
            rediscover = rediscover(now),
            recentlyAdded = recentlyAdded(now),
            genrePicks = genrePicks(now),
        )
        return assembleHomeSections(candidates, homeTime.clock, timeZone)
    }
}

/**
 * Home's sections, or null while the library is empty. They're reloaded when the library or the listening history changes,
 * after [DEBOUNCE] of quiet so an import or a run of plays reloads once, and when the local hour turns, for Around this
 * time's window and Rediscover's daily seed. The first load isn't held back.
 */
class ObserveHomeSections @Inject constructor(
    private val suggestionsRepository: SuggestionsRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val loadHomeSections: LoadHomeSections,
    private val homeTime: HomeTime,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<HomeSection>?> = combine(suggestionsRepository.songCount(), playHistoryRepository.eventCount(), hours()) { songs, events, _ ->
        songs to events
    }
        .withIndex()
        .debounce { if (it.index == 0) 0.milliseconds else DEBOUNCE }
        .mapLatest { (_, counts) -> if (counts.first == 0) null else loadHomeSections(hasHistory = counts.second > 0) }
        .flowOn(dispatcher)

    /** The local hour (with its day), emitted as it turns. */
    private fun hours(): Flow<Pair<Long, Int>> = flow {
        while (true) {
            val now = homeTime.clock.now().toLocalDateTime(homeTime.timeZone())
            emit(now.date.toEpochDays() to now.hour)
            delay((60 - now.minute).times(60).seconds - now.second.seconds)
        }
    }.distinctUntilChanged()

    companion object {
        val DEBOUNCE = 750.milliseconds
    }
}
