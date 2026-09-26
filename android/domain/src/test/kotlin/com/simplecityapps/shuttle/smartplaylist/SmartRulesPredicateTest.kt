package com.simplecityapps.shuttle.smartplaylist

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

class SmartRulesPredicateTest {
    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private val context = SmartRulesContext(
        favouriteSongIds = setOf(7L),
        clock = object : Clock {
            override fun now(): Instant = now
        },
        timeZone = TimeZone.UTC,
    )

    private data class Case(
        val rule: Rule,
        val song: Song,
        val matches: Boolean,
    )

    /** Every case whose rule, on its own, doesn't match (or not match) its song as expected. */
    private fun List<Case>.mismatches(): List<String> = mapNotNull { case ->
        val actual = SmartRules(rules = listOf(case.rule)).predicate(context)(case.song)
        if (actual == case.matches) null else "${case.rule} on ${case.song} matched $actual, expected ${case.matches}"
    }

    @Test
    fun `text operators ignore case`() {
        val title = song(name = "Blue Monday")
        fun text(
            operator: TextOperator,
            value: String,
        ) = Rule.Text(TextField.Title, operator, value)

        listOf(
            Case(text(TextOperator.Is, "blue monday"), title, true),
            Case(text(TextOperator.Is, "Blue"), title, false),
            Case(text(TextOperator.IsNot, "blue monday"), title, false),
            Case(text(TextOperator.IsNot, "Blue"), title, true),
            Case(text(TextOperator.Contains, "MONDAY"), title, true),
            Case(text(TextOperator.Contains, "Tuesday"), title, false),
            Case(text(TextOperator.DoesNotContain, "monday"), title, false),
            Case(text(TextOperator.DoesNotContain, "Tuesday"), title, true),
            Case(text(TextOperator.StartsWith, "blue"), title, true),
            Case(text(TextOperator.StartsWith, "monday"), title, false),
            Case(text(TextOperator.EndsWith, "MONDAY"), title, true),
            Case(text(TextOperator.EndsWith, "blue"), title, false),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `each text field reads its own value`() {
        val song = song(
            name = "Title",
            albumArtist = "Album Artist",
            artists = listOf("First", "Second"),
            album = "Album",
            genres = listOf("Rock", "Pop"),
            grouping = "Grouping",
            path = "/music/rock/song.FLAC",
            mimeType = "audio/flac",
        )
        listOf(
            Case(Rule.Text(TextField.Title, TextOperator.Is, "title"), song, true),
            Case(Rule.Text(TextField.AlbumArtist, TextOperator.Is, "album artist"), song, true),
            Case(Rule.Text(TextField.Album, TextOperator.Is, "album"), song, true),
            Case(Rule.Text(TextField.Grouping, TextOperator.Is, "grouping"), song, true),
            Case(Rule.Text(TextField.Path, TextOperator.StartsWith, "/music/rock/"), song, true),
            Case(Rule.Text(TextField.Path, TextOperator.StartsWith, "/music/pop/"), song, false),
            Case(Rule.Text(TextField.Format, TextOperator.Is, "flac"), song, true),
            Case(Rule.Text(TextField.Format, TextOperator.Is, "mp3"), song, false),
            // No extension: the MIME subtype stands in
            Case(Rule.Text(TextField.Format, TextOperator.Is, "ogg"), song(path = "https://server/stream/42", mimeType = "audio/x-ogg"), true),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `a field with several values matches a positive operator on any value and a negative one on none`() {
        val song = song(artists = listOf("Bowie", "Queen"), genres = listOf("Rock", "Glam"))
        listOf(
            Case(Rule.Text(TextField.Artist, TextOperator.Is, "queen"), song, true),
            Case(Rule.Text(TextField.Artist, TextOperator.Is, "Prince"), song, false),
            Case(Rule.Text(TextField.Artist, TextOperator.IsNot, "queen"), song, false),
            Case(Rule.Text(TextField.Artist, TextOperator.IsNot, "Prince"), song, true),
            Case(Rule.Text(TextField.Genre, TextOperator.Contains, "gla"), song, true),
            Case(Rule.Text(TextField.Genre, TextOperator.DoesNotContain, "gla"), song, false),
            Case(Rule.Text(TextField.Genre, TextOperator.DoesNotContain, "jazz"), song, true),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `a song without a text field matches only the negative operators`() {
        val song = song(name = null, artists = emptyList(), genres = emptyList(), album = null, albumArtist = null)
        TextOperator.entries.flatMap { operator ->
            val negative = operator == TextOperator.IsNot || operator == TextOperator.DoesNotContain
            listOf(TextField.Title, TextField.Artist, TextField.Genre, TextField.Album, TextField.AlbumArtist, TextField.Grouping)
                .map { field -> Case(Rule.Text(field, operator, "x"), song, negative) }
        }.mismatches() shouldBe emptyList()
    }

    @Test
    fun `number conditions`() {
        val song = song(playCount = 5)
        fun count(condition: NumberCondition) = Rule.Number(NumberField.PlayCount, condition)

        listOf(
            Case(count(NumberCondition.Is(5)), song, true),
            Case(count(NumberCondition.Is(4)), song, false),
            Case(count(NumberCondition.IsNot(5)), song, false),
            Case(count(NumberCondition.IsNot(4)), song, true),
            Case(count(NumberCondition.LessThan(6)), song, true),
            Case(count(NumberCondition.LessThan(5)), song, false),
            Case(count(NumberCondition.GreaterThan(4)), song, true),
            Case(count(NumberCondition.GreaterThan(5)), song, false),
            Case(count(NumberCondition.Between(5, 9)), song, true),
            Case(count(NumberCondition.Between(1, 5)), song, true),
            Case(count(NumberCondition.Between(6, 9)), song, false),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `each number field reads its own value, in its own unit`() {
        val song = song(
            date = LocalDate(1983, 3, 7),
            duration = 452_999,
            playCount = 3,
            track = 4,
            disc = 2,
            bitRate = 320,
            sampleRate = 44_100,
            bitDepth = 16,
        )
        listOf(
            Case(Rule.Number(NumberField.Year, NumberCondition.Is(1983)), song, true),
            // Seconds, rounded down
            Case(Rule.Number(NumberField.Duration, NumberCondition.Is(452)), song, true),
            Case(Rule.Number(NumberField.PlayCount, NumberCondition.Is(3)), song, true),
            Case(Rule.Number(NumberField.Track, NumberCondition.Is(4)), song, true),
            Case(Rule.Number(NumberField.Disc, NumberCondition.Is(2)), song, true),
            Case(Rule.Number(NumberField.BitRate, NumberCondition.Is(320)), song, true),
            Case(Rule.Number(NumberField.SampleRate, NumberCondition.Is(44_100)), song, true),
            Case(Rule.Number(NumberField.BitDepth, NumberCondition.Is(16)), song, true),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `a song without a number field matches only is-not`() {
        val song = song(date = null, track = null, disc = null, bitRate = null, sampleRate = null, bitDepth = null)
        val conditions = listOf(
            NumberCondition.Is(1) to false,
            NumberCondition.IsNot(1) to true,
            NumberCondition.LessThan(1) to false,
            NumberCondition.GreaterThan(1) to false,
            NumberCondition.Between(0, 1) to false,
        )
        listOf(NumberField.Year, NumberField.Track, NumberField.Disc, NumberField.BitRate, NumberField.SampleRate, NumberField.BitDepth)
            .flatMap { field -> conditions.map { (condition, matches) -> Case(Rule.Number(field, condition), song, matches) } }
            .mismatches() shouldBe emptyList()
    }

    @Test
    fun `in the last and not in the last count back from now`() {
        val twoDaysAgo = song(lastPlayed = now - 2.days)
        val tenDaysAgo = song(lastPlayed = now - 10.days)
        val never = song(lastPlayed = null)
        fun played(condition: DateCondition) = Rule.Date(DateField.LastPlayed, condition)

        listOf(
            Case(played(DateCondition.InTheLast(7)), twoDaysAgo, true),
            Case(played(DateCondition.InTheLast(7)), tenDaysAgo, false),
            Case(played(DateCondition.InTheLast(7)), never, false),
            Case(played(DateCondition.NotInTheLast(7)), twoDaysAgo, false),
            Case(played(DateCondition.NotInTheLast(7)), tenDaysAgo, true),
            // Never played counts as not played lately
            Case(played(DateCondition.NotInTheLast(7)), never, true),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `before and after leave out the day itself`() {
        val day = LocalDate(2026, 3, 10)
        val startOfDay = Instant.parse("2026-03-10T00:00:00Z")
        fun added(condition: DateCondition) = Rule.Date(DateField.DateAdded, condition)

        listOf(
            Case(added(DateCondition.Before(day)), song(dateAdded = startOfDay - 1.hours), true),
            Case(added(DateCondition.Before(day)), song(dateAdded = startOfDay), false),
            Case(added(DateCondition.After(day)), song(dateAdded = startOfDay + 23.hours), false),
            Case(added(DateCondition.After(day)), song(dateAdded = startOfDay + 24.hours), true),
            Case(added(DateCondition.Before(day)), song(dateAdded = null), false),
            Case(added(DateCondition.After(day)), song(dateAdded = null), false),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `before and after fall in the context's time zone`() {
        val zoned = context.copy(timeZone = TimeZone.of("Australia/Melbourne"))
        // 2026-03-09T20:00Z is already 10 March in Melbourne (UTC+11)
        val song = song(dateAdded = Instant.parse("2026-03-09T20:00:00Z"))
        val before = SmartRules(rules = listOf(Rule.Date(DateField.DateAdded, DateCondition.Before(LocalDate(2026, 3, 10)))))

        before.predicate(context)(song) shouldBe true
        before.predicate(zoned)(song) shouldBe false
    }

    @Test
    fun `each date field reads its own value`() {
        val recent = now - 1.days
        val old = now - 100.days
        val song = song(lastPlayed = recent, lastCompleted = old, dateAdded = recent, lastModified = old)
        listOf(
            Case(Rule.Date(DateField.LastPlayed, DateCondition.InTheLast(7)), song, true),
            Case(Rule.Date(DateField.LastCompleted, DateCondition.InTheLast(7)), song, false),
            Case(Rule.Date(DateField.DateAdded, DateCondition.InTheLast(7)), song, true),
            Case(Rule.Date(DateField.Modified, DateCondition.InTheLast(7)), song, false),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `provider, type and favourite rules`() {
        val plex = song(id = 7, mediaProvider = MediaProviderType.Plex)
        val audiobook = song(id = 8, path = "/music/Book.m4b")
        listOf(
            Case(Rule.Provider(EnumOperator.Is, MediaProviderType.Plex), plex, true),
            Case(Rule.Provider(EnumOperator.Is, MediaProviderType.Jellyfin), plex, false),
            Case(Rule.Provider(EnumOperator.IsNot, MediaProviderType.Plex), plex, false),
            Case(Rule.Provider(EnumOperator.IsNot, MediaProviderType.Jellyfin), plex, true),
            Case(Rule.Type(EnumOperator.Is, Song.Type.Audiobook), audiobook, true),
            Case(Rule.Type(EnumOperator.Is, Song.Type.Audiobook), plex, false),
            Case(Rule.Type(EnumOperator.IsNot, Song.Type.Audiobook), plex, true),
            Case(Rule.Favourite(isFavourite = true), plex, true),
            Case(Rule.Favourite(isFavourite = true), audiobook, false),
            Case(Rule.Favourite(isFavourite = false), plex, false),
            Case(Rule.Favourite(isFavourite = false), audiobook, true),
        ).mismatches() shouldBe emptyList()
    }

    @Test
    fun `match all needs every rule and match any needs one`() {
        val rock = Rule.Text(TextField.Genre, TextOperator.Is, "Rock")
        val eighties = Rule.Number(NumberField.Year, NumberCondition.Between(1980, 1989))
        val rockFrom1983 = song(genres = listOf("Rock"), date = LocalDate(1983, 1, 1))
        val rockFrom1995 = song(genres = listOf("Rock"), date = LocalDate(1995, 1, 1))
        val jazzFrom1995 = song(genres = listOf("Jazz"), date = LocalDate(1995, 1, 1))

        val all = SmartRules(match = SmartRules.Match.All, rules = listOf(rock, eighties)).predicate(context)
        val any = SmartRules(match = SmartRules.Match.Any, rules = listOf(rock, eighties)).predicate(context)

        listOf(rockFrom1983, rockFrom1995, jazzFrom1995).map(all) shouldBe listOf(true, false, false)
        listOf(rockFrom1983, rockFrom1995, jazzFrom1995).map(any) shouldBe listOf(true, true, false)
    }

    @Test
    fun `no rules match every song, whether all or any`() {
        SmartRules(match = SmartRules.Match.All).predicate(context)(song()) shouldBe true
        SmartRules(match = SmartRules.Match.Any).predicate(context)(song()) shouldBe true
    }

    @Test
    fun `usesFavourites is true only with a favourite rule`() {
        SmartRules(rules = listOf(Rule.Text(TextField.Title, TextOperator.Is, "x"))).usesFavourites shouldBe false
        SmartRules(rules = listOf(Rule.Favourite())).usesFavourites shouldBe true
    }
}
