package com.simplecityapps.shuttle.smartplaylist

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A user smart playlist's definition (#506): the songs matching [rules] ([match] all or any of them), in [sort] order,
 * cut to [limit]. No rules matches every song. Compiled to a predicate and evaluated in memory against the library,
 * like the built-in smart playlists; see [SmartRulesEvaluation.kt].
 *
 * Stored as JSON (see [SmartRulesCodec]): each class and constant carries a [SerialName], so renaming one doesn't change
 * what's stored. Property names are the JSON keys, so renaming a property needs a new [SmartRulesCodec.VERSION].
 */
@Serializable
data class SmartRules(
    val match: Match = Match.All,
    val rules: List<Rule> = emptyList(),
    val sort: SmartSort = SmartSort.Default,
    val descending: Boolean = false,
    val limit: Limit? = null,
) {
    @Serializable
    enum class Match {
        @SerialName("all")
        All,

        @SerialName("any")
        Any,
    }
}

/** One condition on a song. Text comparisons ignore case. */
@Serializable
sealed interface Rule {
    @Serializable
    @SerialName("text")
    data class Text(
        val field: TextField,
        val operator: TextOperator,
        val value: String,
    ) : Rule

    @Serializable
    @SerialName("number")
    data class Number(
        val field: NumberField,
        val condition: NumberCondition,
    ) : Rule

    @Serializable
    @SerialName("date")
    data class Date(
        val field: DateField,
        val condition: DateCondition,
    ) : Rule

    /** The song comes from (or, with [EnumOperator.IsNot], doesn't come from) [value]. */
    @Serializable
    @SerialName("provider")
    data class Provider(
        val operator: EnumOperator,
        val value: MediaProviderType,
    ) : Rule

    /** The song is (or isn't) an audiobook, podcast or plain audio, as [Song.type] tells from its path. */
    @Serializable
    @SerialName("type")
    data class Type(
        val operator: EnumOperator,
        val value: Song.Type,
    ) : Rule

    /** The song is (or, with [isFavourite] false, isn't) a favourite. */
    @Serializable
    @SerialName("favourite")
    data class Favourite(
        val isFavourite: Boolean = true,
    ) : Rule
}

@Serializable
enum class TextField {
    @SerialName("title")
    Title,

    /** Any of the song's artists. */
    @SerialName("artist")
    Artist,

    @SerialName("album-artist")
    AlbumArtist,

    @SerialName("album")
    Album,

    /** Any of the song's genres. */
    @SerialName("genre")
    Genre,

    @SerialName("grouping")
    Grouping,

    /** The song's path, or its URL for a server song: "starts with" a folder picks the songs under it. */
    @SerialName("path")
    Path,

    /** The file extension ("flac", "mp3"), or the MIME subtype when the path has none. */
    @SerialName("format")
    Format,
}

/**
 * For a field with several values (artists, genres) the positive operators match when any value does, and the negative
 * ones when none does. A song without the field matches only the negative operators.
 */
@Serializable
enum class TextOperator {
    @SerialName("is")
    Is,

    @SerialName("is-not")
    IsNot,

    @SerialName("contains")
    Contains,

    @SerialName("does-not-contain")
    DoesNotContain,

    @SerialName("starts-with")
    StartsWith,

    @SerialName("ends-with")
    EndsWith,
}

@Serializable
enum class NumberField {
    @SerialName("year")
    Year,

    /** In seconds. */
    @SerialName("duration")
    Duration,

    @SerialName("play-count")
    PlayCount,

    @SerialName("track")
    Track,

    @SerialName("disc")
    Disc,

    /** In kb/s. */
    @SerialName("bit-rate")
    BitRate,

    /** In Hz. */
    @SerialName("sample-rate")
    SampleRate,

    @SerialName("bit-depth")
    BitDepth,
}

/** A song without the field matches only [IsNot]. Bounds are inclusive. */
@Serializable
sealed interface NumberCondition {
    @Serializable
    @SerialName("is")
    data class Is(val value: Long) : NumberCondition

    @Serializable
    @SerialName("is-not")
    data class IsNot(val value: Long) : NumberCondition

    @Serializable
    @SerialName("less-than")
    data class LessThan(val value: Long) : NumberCondition

    @Serializable
    @SerialName("greater-than")
    data class GreaterThan(val value: Long) : NumberCondition

    @Serializable
    @SerialName("between")
    data class Between(
        val min: Long,
        val max: Long,
    ) : NumberCondition
}

@Serializable
enum class DateField {
    @SerialName("last-played")
    LastPlayed,

    @SerialName("last-completed")
    LastCompleted,

    @SerialName("date-added")
    DateAdded,

    @SerialName("modified")
    Modified,
}

/**
 * A song without the date (never played, say) matches only [NotInTheLast]. [Before] and [After] exclude the day
 * itself, in the device's time zone.
 */
@Serializable
sealed interface DateCondition {
    @Serializable
    @SerialName("in-the-last")
    data class InTheLast(val days: Int) : DateCondition

    @Serializable
    @SerialName("not-in-the-last")
    data class NotInTheLast(val days: Int) : DateCondition

    @Serializable
    @SerialName("before")
    data class Before(val date: LocalDate) : DateCondition

    @Serializable
    @SerialName("after")
    data class After(val date: LocalDate) : DateCondition
}

@Serializable
enum class EnumOperator {
    @SerialName("is")
    Is,

    @SerialName("is-not")
    IsNot,
}

/**
 * The order a smart playlist's songs play in, ascending unless [SmartRules.descending]; ties fall back to album order.
 * Separate from the library's [com.simplecityapps.shuttle.sorting.SongSortOrder], whose orders each have a fixed
 * direction and which has no [Random].
 */
@Serializable
enum class SmartSort {
    /** Album order: album, album artist, disc, track. */
    @SerialName("default")
    Default,

    @SerialName("title")
    Title,

    @SerialName("artist")
    Artist,

    @SerialName("album")
    Album,

    @SerialName("year")
    Year,

    @SerialName("duration")
    Duration,

    @SerialName("play-count")
    PlayCount,

    @SerialName("last-played")
    LastPlayed,

    @SerialName("last-completed")
    LastCompleted,

    @SerialName("date-added")
    DateAdded,

    @SerialName("modified")
    Modified,

    /** Shuffled by a seed chosen per evaluation, so the order holds while the library changes underneath it. */
    @SerialName("random")
    Random,
}

/** Applied after the sort: the first [Songs.count] songs, or as many as fit in [Minutes.minutes]. */
@Serializable
sealed interface Limit {
    @Serializable
    @SerialName("songs")
    data class Songs(val count: Int) : Limit

    @Serializable
    @SerialName("minutes")
    data class Minutes(val minutes: Int) : Limit
}
