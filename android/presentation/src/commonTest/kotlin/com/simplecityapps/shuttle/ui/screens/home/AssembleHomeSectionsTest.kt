package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.shuttle.ui.text.StringKey
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class AssembleHomeSectionsTest {
    private val zone = TimeZone.of("Australia/Melbourne")

    // Wednesday 23 September 2026, 8am in Melbourne (UTC+10)
    private val wednesdayMorning = Instant.parse("2026-09-22T22:00:00Z")
    private val saturdayMorning = Instant.parse("2026-09-25T22:00:00Z")

    private fun album(name: String): HomeItem = HomeItem.AlbumItem(createAlbum(name, "artist"))

    private fun artist(name: String): HomeItem = HomeItem.ArtistItem(createAlbumArtist(name))

    private fun genre(name: String): HomeItem = HomeItem.GenreItem(createGenre(name))

    private val albums = (1..30).map { album("album $it") }
    private val genres = (1..8).map { genre("genre $it") }

    private val empty = HomeCandidates(
        hasHistory = true,
        jumpBackIn = JumpBackInCandidates(emptyList(), emptyList()),
        aroundThisTime = emptyList(),
        heavyRotation = emptyList(),
        rediscover = emptyList(),
        recentlyAdded = emptyList(),
        genrePicks = GenrePickCandidates(emptyList(), emptyList()),
    )

    private fun assemble(
        candidates: HomeCandidates,
        at: Instant = wednesdayMorning,
    ) = assembleHomeSections(candidates, at, zone)

    private fun List<HomeSection>.section(id: HomeSectionId) = single { it.id == id }

    private fun around(
        item: HomeItem,
        days: Int,
        weekendDays: Int = 0,
    ) = AroundThisTimeCandidate(item, days, weekendDays)

    private fun rotation(
        item: HomeItem,
        days: Int,
    ) = HeavyRotationCandidate(item, days, lastPlayedAt = wednesdayMorning)

    @Test
    fun `sections come in order and an item shows only in the earliest`() {
        val sections = assemble(
            empty.copy(
                jumpBackIn = JumpBackInCandidates(albums.take(2), emptyList()),
                aroundThisTime = albums.subList(1, 5).map { around(it, days = 3) },
                heavyRotation = listOf(rotation(albums[0], 6), rotation(albums[9], 5), rotation(albums[12], 4)),
                rediscover = listOf(albums[9], albums[10], albums[13]),
                recentlyAdded = listOf(albums[10], albums[11], albums[14]),
                genrePicks = GenrePickCandidates(emptyList(), genres),
            ),
        )

        sections.map { it.id } shouldBe listOf(
            HomeSectionId.JumpBackIn,
            HomeSectionId.AroundThisTime,
            HomeSectionId.HeavyRotation,
            HomeSectionId.Rediscover,
            HomeSectionId.RecentlyAdded,
            HomeSectionId.GenrePicks,
        )
        sections.section(HomeSectionId.AroundThisTime).items shouldBe albums.subList(2, 5)
        sections.section(HomeSectionId.HeavyRotation).items shouldBe listOf(albums[9], albums[12])
        sections.section(HomeSectionId.Rediscover).items.toSet() shouldBe setOf(albums[10], albums[13])
        sections.section(HomeSectionId.RecentlyAdded).items shouldBe listOf(albums[11], albums[14])
        sections.flatMap { it.items }.map { it.key }.let { keys -> keys shouldBe keys.distinct() }
    }

    @Test
    fun `while candidates load the sections stop at the first still loading each as it will be once all have`() {
        val loaded = empty.copy(
            jumpBackIn = JumpBackInCandidates(albums.take(2), emptyList()),
            aroundThisTime = albums.subList(1, 5).map { around(it, days = 3) },
            heavyRotation = listOf(rotation(albums[0], 6), rotation(albums[9], 5), rotation(albums[12], 4)),
            rediscover = listOf(albums[9], albums[10], albums[13]),
            recentlyAdded = listOf(albums[10], albums[11], albums[14]),
            genrePicks = GenrePickCandidates(emptyList(), genres),
        )
        val all = assemble(loaded)

        assemble(loaded.copy(jumpBackIn = null)) shouldBe emptyList()
        assemble(loaded.copy(heavyRotation = null)) shouldBe all.take(2)
        assemble(loaded.copy(heavyRotation = null, genrePicks = null)) shouldBe all.take(2)
        assemble(loaded.copy(genrePicks = null)) shouldBe all.take(5)
        loaded.complete shouldBe true
        loaded.copy(genrePicks = null).complete shouldBe false
    }

    @Test
    fun `a cold start waits on jump back in to choose then shows recently added before genre picks and shuffle all load`() {
        val loaded = empty.copy(hasHistory = false, recentlyAdded = albums.take(4), genrePicks = GenrePickCandidates(emptyList(), genres))

        assemble(loaded.copy(jumpBackIn = null)) shouldBe emptyList()
        assemble(loaded.copy(genrePicks = null)).map { it.id } shouldBe listOf(HomeSectionId.RecentlyAdded)
        assemble(loaded).map { it.id } shouldBe listOf(HomeSectionId.RecentlyAdded, HomeSectionId.GenrePicks, HomeSectionId.ShuffleAll)
    }

    @Test
    fun `an eligible around this time that overlaps jump back in still shows what's left`() {
        val sections = assemble(
            empty.copy(
                jumpBackIn = JumpBackInCandidates(listOf(albums[0], albums[20]), emptyList()),
                aroundThisTime = albums.take(3).map { around(it, days = 3) },
            ),
        )

        sections.section(HomeSectionId.AroundThisTime).items shouldBe albums.subList(1, 3)
    }

    @Test
    fun `a section left with one item after earlier sections claim theirs hides`() {
        val sections = assemble(
            empty.copy(
                jumpBackIn = JumpBackInCandidates(albums.take(2), emptyList()),
                aroundThisTime = albums.take(3).map { around(it, days = 3) },
            ),
        )

        sections.map { it.id } shouldBe listOf(HomeSectionId.JumpBackIn)
    }

    @Test
    fun `a hidden section claims none of its items`() {
        val sections = assemble(
            empty.copy(
                // Two contexts are too few for Around this time, so Rediscover keeps them
                aroundThisTime = albums.take(2).map { around(it, days = 5) },
                rediscover = albums.take(2),
            ),
        )

        sections.map { it.id } shouldBe listOf(HomeSectionId.Rediscover)
        sections.single().items.toSet() shouldBe albums.take(2).toSet()
    }

    @Test
    fun `jump back in shows the last eight contexts`() {
        val sections = assemble(empty.copy(jumpBackIn = JumpBackInCandidates(albums.take(10), albums.drop(20))))

        sections.section(HomeSectionId.JumpBackIn).items shouldBe albums.take(8)
    }

    @Test
    fun `jump back in falls back to the albums last played through until the history has contexts`() {
        val candidates = empty.copy(hasHistory = false, jumpBackIn = JumpBackInCandidates(emptyList(), albums.take(3)))

        assemble(candidates).section(HomeSectionId.JumpBackIn).items shouldBe albums.take(3)
    }

    @Test
    fun `cold start shows recently added the largest genres and shuffle all`() {
        val candidates = empty.copy(
            hasHistory = false,
            rediscover = albums.take(3),
            recentlyAdded = albums.take(4),
            genrePicks = GenrePickCandidates(played = listOf(genre("played")), largest = genres),
        )

        val sections = assemble(candidates)

        sections.map { it.id } shouldBe listOf(HomeSectionId.RecentlyAdded, HomeSectionId.GenrePicks, HomeSectionId.ShuffleAll)
        sections.section(HomeSectionId.GenrePicks).items.toSet() shouldBe genres.take(6).toSet()
        sections.section(HomeSectionId.GenrePicks).subtitle shouldBe StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE
        sections.section(HomeSectionId.ShuffleAll).items shouldBe emptyList()
    }

    @Test
    fun `around this time needs three contexts with three days each`() {
        val twoDays = around(albums[3], days = 2)
        val enough = albums.take(3).map { around(it, days = 3) }

        assemble(empty.copy(aroundThisTime = enough + twoDays)).section(HomeSectionId.AroundThisTime).items shouldBe albums.take(3)
        assemble(empty.copy(aroundThisTime = enough.take(2) + around(albums[2], days = 2))).map { it.id } shouldBe emptyList()
    }

    @Test
    fun `around this time weighs days of today's kind one and a half times`() {
        // 4 weekday days score 6 on a weekday; 5 weekend days score 7.5 at a weekend
        val weekdayRegular = around(albums[0], days = 4, weekendDays = 0)
        val weekendRegular = around(albums[1], days = 5, weekendDays = 5)
        val filler = albums.subList(2, 4).map { around(it, days = 3) }

        assemble(empty.copy(aroundThisTime = listOf(weekendRegular, weekdayRegular) + filler), at = wednesdayMorning)
            .section(HomeSectionId.AroundThisTime).items.take(2) shouldBe listOf(albums[0], albums[1])
        assemble(empty.copy(aroundThisTime = listOf(weekdayRegular, weekendRegular) + filler), at = saturdayMorning)
            .section(HomeSectionId.AroundThisTime).items.take(2) shouldBe listOf(albums[1], albums[0])
    }

    @Test
    fun `around this time is titled for the part of the day`() {
        val candidates = empty.copy(aroundThisTime = albums.take(3).map { around(it, days = 3) })

        assemble(candidates).section(HomeSectionId.AroundThisTime).title shouldBe HomeSectionTitle.ThisMorning
        assemble(candidates, at = Instant.parse("2026-09-23T04:00:00Z")).section(HomeSectionId.AroundThisTime).title shouldBe HomeSectionTitle.ThisAfternoon
        assemble(candidates, at = Instant.parse("2026-09-23T11:00:00Z")).section(HomeSectionId.AroundThisTime).title shouldBe HomeSectionTitle.Tonight
        partOfDay(3) shouldBe HomeSectionTitle.Tonight
        partOfDay(4) shouldBe HomeSectionTitle.ThisMorning
        partOfDay(12) shouldBe HomeSectionTitle.ThisAfternoon
        partOfDay(18) shouldBe HomeSectionTitle.Tonight
    }

    @Test
    fun `heavy rotation needs three days and keeps the order it's given`() {
        val candidates = empty.copy(
            heavyRotation = listOf(rotation(artist("often"), days = 9), rotation(albums[0], days = 3), rotation(albums[1], days = 2)),
        )

        assemble(candidates).section(HomeSectionId.HeavyRotation).items shouldBe listOf(artist("often"), albums[0])
        assemble(empty.copy(heavyRotation = listOf(rotation(albums[0], 2), rotation(albums[1], 2)))).map { it.id } shouldBe emptyList()
    }

    @Test
    fun `every section but shuffle all says what it is`() {
        val sections = assemble(
            empty.copy(
                jumpBackIn = JumpBackInCandidates(albums.take(2), emptyList()),
                aroundThisTime = albums.subList(2, 5).map { around(it, days = 3) },
                heavyRotation = albums.subList(5, 7).map { rotation(it, days = 3) },
                rediscover = albums.subList(7, 9),
                recentlyAdded = albums.subList(9, 11),
                genrePicks = GenrePickCandidates(listOf(genre("jazz")), genres),
            ),
        )

        sections.map { it.subtitle } shouldBe listOf(
            null,
            StringKey.HOME_AROUND_THIS_TIME_SUBTITLE,
            StringKey.HOME_HEAVY_ROTATION_SUBTITLE,
            StringKey.HOME_REDISCOVER_SUBTITLE,
            StringKey.HOME_RECENTLY_ADDED_SUBTITLE,
            StringKey.HOME_GENRE_PICKS_SUBTITLE,
        )
        assemble(empty.copy(hasHistory = false, recentlyAdded = albums.take(2))).map { it.subtitle } shouldBe
            listOf(StringKey.HOME_RECENTLY_ADDED_SUBTITLE, null)
    }

    @Test
    fun `rediscover holds its order through a day and reshuffles the next`() {
        val candidates = empty.copy(rediscover = albums)
        val morning = assemble(candidates).section(HomeSectionId.Rediscover).items
        val evening = assemble(candidates, at = wednesdayMorning + 12.hours).section(HomeSectionId.Rediscover).items
        val tomorrow = assemble(candidates, at = wednesdayMorning + 24.hours).section(HomeSectionId.Rediscover).items

        morning.size shouldBe 10
        evening shouldBe morning
        (tomorrow == morning) shouldBe false
    }

    @Test
    fun `recently added shows the newest albums on a first import too`() {
        val items = albums.take(3)

        assemble(empty.copy(recentlyAdded = items)).section(HomeSectionId.RecentlyAdded).items shouldBe items
        assemble(empty.copy(hasHistory = false, recentlyAdded = items)).map { it.id } shouldBe listOf(HomeSectionId.RecentlyAdded, HomeSectionId.ShuffleAll)
    }

    @Test
    fun `genre picks choose played genres first fill with the largest and need four`() {
        val played = listOf(genre("jazz"), genres[1])

        val picks = assemble(empty.copy(genrePicks = GenrePickCandidates(played, genres))).section(HomeSectionId.GenrePicks)
        picks.items.toSet() shouldBe setOf(genre("jazz"), genres[1], genres[0], genres[2], genres[3], genres[4])
        picks.subtitle shouldBe StringKey.HOME_GENRE_PICKS_SUBTITLE
        assemble(empty.copy(genrePicks = GenrePickCandidates(played, genres.take(3)))).section(HomeSectionId.GenrePicks).items.size shouldBe 4
        assemble(empty.copy(genrePicks = GenrePickCandidates(played, genres.take(1)))).map { it.id } shouldBe emptyList()
    }

    @Test
    fun `genre picks say they're the largest when no played genre is left to show`() {
        val candidates = empty.copy(
            jumpBackIn = JumpBackInCandidates(listOf(genre("jazz"), albums[0]), emptyList()),
            genrePicks = GenrePickCandidates(listOf(genre("jazz")), genres),
        )

        assemble(candidates).section(HomeSectionId.GenrePicks).subtitle shouldBe StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE
    }

    @Test
    fun `genre picks hold their order through a day however the plays rank them`() {
        val morning = assemble(empty.copy(genrePicks = GenrePickCandidates(listOf(genres[0], genres[1]), genres))).section(HomeSectionId.GenrePicks).items
        val evening = assemble(empty.copy(genrePicks = GenrePickCandidates(listOf(genres[1], genres[0]), genres)), at = wednesdayMorning + 12.hours)
            .section(HomeSectionId.GenrePicks).items

        evening shouldBe morning
    }
}
