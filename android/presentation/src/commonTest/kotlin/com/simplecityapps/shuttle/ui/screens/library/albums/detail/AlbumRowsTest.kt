package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.text.PluralKey
import com.simplecityapps.shuttle.ui.text.UiText
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class AlbumRowsTest {

    private var nextId = 1L

    private fun movement(work: String?, name: String, composer: String? = "Ludwig Harrow", track: Int = 1, disc: Int = 1, conductor: String? = null): Song = createSong(id = nextId++, name = name)
        .copy(work = work, composer = composer, composerSort = composer?.let { "${it.substringAfterLast(' ')}, ${it.substringBeforeLast(' ')}" }, track = track, disc = disc, conductor = conductor, genres = listOf("Classical"))

    private fun sonata(composer: String? = "Ludwig Harrow") = listOf(
        movement("Sonata No. 3 in C minor, Op. 10", "I. Allegro", composer, track = 1),
        movement("Sonata No. 3 in C minor, Op. 10", "II. Adagio", composer, track = 2),
        movement("Sonata No. 3 in C minor, Op. 10", "III. Finale", composer, track = 3),
    )

    @Test
    fun `a work becomes a header over its movements with their numerals`() {
        val rows = buildAlbumRows(sonata(), albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows

        val header = rows[0].shouldBeInstanceOf<AlbumRow.WorkHeader>()
        header.title shouldBe "Sonata No. 3"
        header.detail shouldBe "C minor · Op. 10"
        header.count shouldBe UiText.Plural(PluralKey.WORK_MOVEMENTS, 3)
        rows.drop(1).map { (it as AlbumRow.Movement).numeral to it.name } shouldBe listOf("I" to "Allegro", "II" to "Adagio", "III" to "Finale")
    }

    @Test
    fun `a single composer album has an eyebrow and no composer headings`() {
        val result = buildAlbumRows(sonata(), albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!

        result.eyebrow shouldBe "Harrow"
        result.rows.none { it is AlbumRow.ComposerHeading } shouldBe true
    }

    @Test
    fun `a recital gets a heading only where the composer changes`() {
        val songs = sonata("Ludwig Harrow") + listOf(
            movement("Prelude in D", "I. Slow", "Maud Verrin"),
            movement("Prelude in D", "II. Fast", "Maud Verrin"),
            movement("Nocturne in F", "I. Calm", "Maud Verrin"),
            movement("Nocturne in F", "II. Stormy", "Maud Verrin"),
        )

        val result = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!

        result.eyebrow shouldBe null
        result.rows.filterIsInstance<AlbumRow.ComposerHeading>().map { it.name } shouldBe listOf("Harrow", "Verrin")
    }

    @Test
    fun `a suite by the album artist shows the eyebrow but no composer heading`() {
        val songs = sonata("Ines Varga")

        val result = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!

        result.eyebrow shouldBe "Varga"
        result.rows.none { it is AlbumRow.ComposerHeading } shouldBe true
    }

    @Test
    fun `Beethoven with the composer as album artist and a bonus track still has the eyebrow`() {
        val songs = listOf("I. Allegro con brio", "II. Andante con moto").map {
            movement("Symphony no. 5 in C minor, op. 67", it, "Ludwig van Beethoven").copy(composerSort = "Beethoven, Ludwig van")
        } + movement(null, "Bonus", composer = null)

        buildAlbumRows(songs, albumArtist = "Ludwig van Beethoven", currentSongId = null, toggled = emptySet())!!.eyebrow shouldBe "Beethoven"
    }

    @Test
    fun `a pop album with a grouped run and the artist as composer has no eyebrow`() {
        val songs = listOf("Suite: I", "Suite: II").mapIndexed { i, name ->
            movement(null, name, "Ines Varga", track = i + 1).copy(genres = listOf("Pop"))
        }

        buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())?.eyebrow shouldBe null
    }

    @Test
    fun `a pop album with a shared work tag has no eyebrow`() {
        val songs = sonata("Ines Varga").map { it.copy(genres = listOf("Pop")) }

        buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())?.eyebrow shouldBe null
    }

    @Test
    fun `Mahler as album artist in Classical keeps the eyebrow`() {
        val songs = listOf("I. Langsam", "II. Scherzo").mapIndexed { i, name ->
            movement("Symphony No. 1 in D", name, "Gustav Mahler", track = i + 1).copy(composerSort = "Mahler, Gustav")
        }

        buildAlbumRows(songs, albumArtist = "Gustav Mahler", currentSongId = null, toggled = emptySet())!!.eyebrow shouldBe "Mahler"
    }

    @Test
    fun `one tagged song among untagged songs has no eyebrow`() {
        val songs = sonata(composer = null).let { it.take(2) + it[2].copy(composer = "Ludwig Harrow", composerSort = "Harrow, Ludwig") }

        buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.eyebrow shouldBe null
    }

    @Test
    fun `plain songs among works stay plain tracks`() {
        val songs = sonata() + movement(null, "Encore", composer = null)

        val rows = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows

        rows.last().shouldBeInstanceOf<AlbumRow.Track>().song.name shouldBe "Encore"
    }

    @Test
    fun `an album with no work blocks has no rows`() {
        buildAlbumRows(listOf(movement(null, "One", null), movement(null, "Two", null)), albumArtist = null, currentSongId = null, toggled = emptySet()) shouldBe null
    }

    @Test
    fun `a disc change inside a work gets a divider before its movement`() {
        val songs = listOf(
            movement("Sonata No. 3 in C minor, Op. 10", "I. Allegro", track = 1, disc = 1),
            movement("Sonata No. 3 in C minor, Op. 10", "II. Adagio", track = 2, disc = 1),
            movement("Sonata No. 3 in C minor, Op. 10", "III. Finale", track = 1, disc = 2),
        )

        val rows = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows

        rows.map { it::class.simpleName } shouldBe listOf("WorkHeader", "Movement", "Movement", "DiscDivider", "Movement")
        (rows[3] as AlbumRow.DiscDivider).disc shouldBe 2
        (rows[3] as AlbumRow.DiscDivider).inWork shouldBe true
    }

    @Test
    fun `a movement after a disc split plays from its index in the album`() {
        val songs = listOf(
            movement("Sonata No. 3 in C minor, Op. 10", "I. Allegro", track = 1, disc = 1),
            movement("Sonata No. 3 in C minor, Op. 10", "II. Adagio", track = 2, disc = 1),
            movement("Sonata No. 3 in C minor, Op. 10", "III. Finale", track = 1, disc = 2),
        )

        val rows = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows

        rows.filterIsInstance<AlbumRow.Movement>().map { it.playIndex } shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `a collapsed work hides its movements and its header plays the whole work`() {
        val songs = (1..21).map { n -> movement("Suite No. 1", "Part $n", track = n) } + (1..21).map { n -> movement("Suite No. 2", "Part $n", track = n) }

        val rows = buildAlbumRows(songs, albumArtist = null, currentSongId = null, toggled = emptySet())!!.rows

        rows.size shouldBe 2
        val header = rows[0].shouldBeInstanceOf<AlbumRow.WorkHeader>()
        header.tap shouldBe WorkTap.ToggleCollapse
        header.songs.map { it.id } shouldBe songs.take(21).map { it.id }
    }

    @Test
    fun `a short work taps to play`() {
        val header = buildAlbumRows(sonata(), albumArtist = null, currentSongId = null, toggled = emptySet())!!.rows[0].shouldBeInstanceOf<AlbumRow.WorkHeader>()

        header.tap shouldBe WorkTap.Play
    }

    @Test
    fun `a plain song with another composer removes the eyebrow`() {
        val songs = sonata() + movement(null, "Encore", composer = "Maud Verrin")

        buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.eyebrow shouldBe null
    }

    @Test
    fun `a classical plain track keeps its movement label`() {
        val songs = sonata() + movement(null, "Encore", composer = null).copy(movementName = "Allegro", movementNumber = 4)

        val track = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows.last().shouldBeInstanceOf<AlbumRow.Track>()

        track.title shouldBe "IV. Allegro"
        track.playIndex shouldBe 3
    }

    @Test
    fun `an album starting with a work on disc one has no leading disc divider`() {
        val songs = sonata() + listOf(
            movement("Prelude in D", "I. Slow", track = 1, disc = 2),
            movement("Prelude in D", "II. Fast", track = 2, disc = 2),
        )

        val rows = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = null, toggled = emptySet())!!.rows

        rows.map { it::class.simpleName } shouldBe listOf("WorkHeader", "Movement", "Movement", "Movement", "DiscDivider", "WorkHeader", "Movement", "Movement")
    }

    @Test
    fun `the playing movement marks its row and its work header`() {
        val songs = sonata()

        val rows = buildAlbumRows(songs, albumArtist = "Ines Varga", currentSongId = songs[1].id, toggled = emptySet())!!.rows

        (rows[0] as AlbumRow.WorkHeader).playing shouldBe true
        rows.filterIsInstance<AlbumRow.Movement>().map { it.playing } shouldBe listOf(false, true, false)
    }

    @Test
    fun `a work whose credits differ from the album's carries its own`() {
        val songs = sonata().map { it.copy(conductor = "Ines Varga") } + listOf(
            movement("Prelude in D", "I. Slow", conductor = "Tomas Reyes"),
            movement("Prelude in D", "II. Fast", conductor = "Tomas Reyes"),
            movement("Prelude in D", "III. Coda", conductor = "Tomas Reyes"),
        ).let { it } + sonata().map { it.copy(id = nextId++, conductor = "Ines Varga", work = "Sonata No. 4 in D, Op. 11") }

        val headers = buildAlbumRows(songs, albumArtist = null, currentSongId = null, toggled = emptySet())!!.rows.filterIsInstance<AlbumRow.WorkHeader>()

        headers.map { it.credits != null } shouldBe listOf(false, true, false)
    }

    @Test
    fun `a long multi work album starts collapsed and a toggle expands one work`() {
        val songs = (1..2).flatMap { work -> (1..21).map { n -> movement("Suite No. $work", "Part $n", track = n) } }

        val collapsed = buildAlbumRows(songs, albumArtist = null, currentSongId = null, toggled = emptySet())!!.rows
        collapsed.map { it::class.simpleName } shouldBe listOf("WorkHeader", "WorkHeader")
        (collapsed[0] as AlbumRow.WorkHeader).collapsible shouldBe true

        val firstId = songs.first().id
        val expanded = buildAlbumRows(songs, albumArtist = null, currentSongId = null, toggled = setOf(firstId))!!.rows
        expanded.size shouldBe 23
        (expanded[0] as AlbumRow.WorkHeader).collapsed shouldBe false
    }
}
