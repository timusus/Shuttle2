package com.simplecityapps.shuttle.smartplaylist

import com.simplecityapps.mediaprovider.repository.songs.SongComparator
import com.simplecityapps.shuttle.model.Song
import java.text.Collator
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** What rules are evaluated against besides the song: the time now, and the zone dates fall in. */
data class SmartRulesContext(
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
)

/** Whether a song matches all (or any) of the rules; every song does when there are none. */
fun SmartRules.predicate(context: SmartRulesContext): (Song) -> Boolean {
    val predicates = rules.map { rule -> rule.predicate(context) }
    return when {
        predicates.isEmpty() -> { _ -> true }
        match == SmartRules.Match.All -> { song -> predicates.all { predicate -> predicate(song) } }
        else -> { song -> predicates.any { predicate -> predicate(song) } }
    }
}

/** [songs] (already matched by [predicate]) in the rules' sort order, cut to their limit. [seed] fixes a random order. */
fun SmartRules.sortAndLimit(
    songs: List<Song>,
    seed: Long,
): List<Song> = songs.sortedWith(comparator(seed)).limitedTo(limit)

fun SmartRules.comparator(seed: Long): Comparator<Song> {
    val key: Comparator<Song> =
        when (sort) {
            SmartSort.Default -> SongComparator.defaultComparator
            SmartSort.Title -> compareBy(nullsFirst(collator)) { song -> song.name }
            SmartSort.Artist -> compareBy(nullsFirst(collator)) { song -> song.albumArtistGroupKey.key }
            SmartSort.Album -> compareBy(nullsFirst(collator)) { song -> song.albumGroupKey.key }
            SmartSort.Year -> compareBy { song -> song.date }
            SmartSort.Duration -> compareBy { song -> song.duration }
            SmartSort.PlayCount -> compareBy { song -> song.playCount }
            SmartSort.LastPlayed -> compareBy { song -> song.lastPlayed }
            SmartSort.LastCompleted -> compareBy { song -> song.lastCompleted }
            SmartSort.DateAdded -> compareBy { song -> song.dateAdded }
            SmartSort.Modified -> compareBy { song -> song.lastModified }
            SmartSort.Random -> compareBy<Song> { song -> mix(seed xor song.id) }.thenBy { song -> song.id }
        }
    return (if (descending) key.reversed() else key).then(SongComparator.defaultComparator)
}

private fun List<Song>.limitedTo(limit: Limit?): List<Song> = when (limit) {
    null -> this

    is Limit.Songs -> take(limit.count.coerceAtLeast(0))

    is Limit.Minutes -> {
        val budget = limit.minutes.toLong() * 60_000
        var total = 0L
        takeWhile { song ->
            total += song.duration
            total <= budget
        }
    }
}

private val collator: Collator by lazy { Collator.getInstance().apply { strength = Collator.SECONDARY } }

/** SplitMix64's finaliser: spreads consecutive ids (and seeds) across the whole range. */
private fun mix(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

private fun Rule.predicate(context: SmartRulesContext): (Song) -> Boolean = when (this) {
    is Rule.Text -> predicate()
    is Rule.Number -> predicate()
    is Rule.Date -> predicate(context)
    is Rule.Provider -> { song -> (song.mediaProvider == value) == (operator == EnumOperator.Is) }
    is Rule.Type -> { song -> (song.type == value) == (operator == EnumOperator.Is) }
    is Rule.Favourite -> { song -> song.isFavourite == isFavourite }
}

private fun Rule.Text.predicate(): (Song) -> Boolean {
    val matches: (String) -> Boolean =
        when (operator) {
            TextOperator.Is, TextOperator.IsNot -> { text -> text.equals(value, ignoreCase = true) }
            TextOperator.Contains, TextOperator.DoesNotContain -> { text -> text.contains(value, ignoreCase = true) }
            TextOperator.StartsWith -> { text -> text.startsWith(value, ignoreCase = true) }
            TextOperator.EndsWith -> { text -> text.endsWith(value, ignoreCase = true) }
        }
    val negated = operator == TextOperator.IsNot || operator == TextOperator.DoesNotContain
    return { song -> field.values(song).any(matches) != negated }
}

private fun TextField.values(song: Song): List<String> = when (this) {
    TextField.Title -> listOfNotNull(song.name)
    TextField.Artist -> song.artists
    TextField.AlbumArtist -> listOfNotNull(song.albumArtist)
    TextField.Album -> listOfNotNull(song.album)
    TextField.Genre -> song.genres
    TextField.Grouping -> listOfNotNull(song.grouping)
    TextField.Path -> listOf(song.path)
    TextField.Format -> listOfNotNull(song.format)
}

private val Song.format: String?
    get() = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "").ifEmpty { null }
        ?: mimeType.substringAfter('/', missingDelimiterValue = "").substringBefore(';').removePrefix("x-").ifEmpty { null }

private fun Rule.Number.predicate(): (Song) -> Boolean = { song ->
    val number = field.value(song)
    when (condition) {
        is NumberCondition.Is -> number == condition.value
        is NumberCondition.IsNot -> number != condition.value
        is NumberCondition.LessThan -> number != null && number < condition.value
        is NumberCondition.GreaterThan -> number != null && number > condition.value
        is NumberCondition.Between -> number != null && number in condition.min..condition.max
    }
}

private fun NumberField.value(song: Song): Long? = when (this) {
    NumberField.Year -> song.date?.year
    NumberField.Duration -> song.duration / 1000
    NumberField.PlayCount -> song.playCount
    NumberField.Track -> song.track
    NumberField.Disc -> song.disc
    NumberField.BitRate -> song.bitRate
    NumberField.SampleRate -> song.sampleRate
    NumberField.BitDepth -> song.bitDepth
}?.toLong()

private fun Rule.Date.predicate(context: SmartRulesContext): (Song) -> Boolean = when (condition) {
    is DateCondition.InTheLast -> dateMatches { date -> date > context.clock.now() - condition.days.days }
    is DateCondition.NotInTheLast -> { song -> field.value(song)?.let { date -> date <= context.clock.now() - condition.days.days } ?: true }
    is DateCondition.Before -> condition.date.atStartOfDayIn(context.timeZone).let { start -> dateMatches { date -> date < start } }
    is DateCondition.After -> condition.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(context.timeZone).let { end -> dateMatches { date -> date >= end } }
}

/** A song without the date matches none of these. */
private fun Rule.Date.dateMatches(test: (Instant) -> Boolean): (Song) -> Boolean = { song -> field.value(song)?.let(test) ?: false }

private fun DateField.value(song: Song): Instant? = when (this) {
    DateField.LastPlayed -> song.lastPlayed
    DateField.LastCompleted -> song.lastCompleted
    DateField.DateAdded -> song.dateAdded
    DateField.Modified -> song.lastModified
}
