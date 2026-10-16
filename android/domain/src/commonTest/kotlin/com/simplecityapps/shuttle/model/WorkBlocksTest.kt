package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class WorkBlocksTest {
    private fun movement(id: Long, work: String?, name: String, track: Int, disc: Int = 1, composer: String = "Beethoven", duration: Int = 60_000) = song(id = id, name = name, albumArtist = "Berliner Philharmoniker", track = track, disc = disc, duration = duration, genres = listOf("Classical"))
        .copy(composer = composer, composerSort = "$composer, Ludwig van", conductor = "Kleiber", work = work)

    private fun symphony(startId: Long = 1, work: String = "Symphony No. 5", count: Int = 4, disc: Int = 1) = List(count) { movement(startId + it, work, listOf("Allegro con brio", "Andante con moto", "Scherzo", "Allegro")[it % 4], it + 1, disc) }

    @Test
    fun `consecutive movements of one work form a block`() {
        val items = groupWorks(symphony() + movement(9, null, "Bonus", 5))
        items.size shouldBe 2
        val block = (items[0] as AlbumItem.Work).block
        block.movementCount shouldBe 4
        block.durationMs shouldBe 240_000L
        block.title.core shouldBe "Symphony No. 5"
        block.movements.map { it.label } shouldBe listOf("I. Allegro con brio", "II. Andante con moto", "III. Scherzo", "IV. Allegro")
        items[1] shouldBe AlbumItem.Track(movement(9, null, "Bonus", 5))
    }

    @Test
    fun `a single song of a work stays a track`() {
        groupWorks(listOf(movement(1, "Symphony No. 5", "Allegro", 1))).single() shouldBe AlbumItem.Track(movement(1, "Symphony No. 5", "Allegro", 1))
    }

    @Test
    fun `work and composer are compared after normalising`() {
        val songs = listOf(
            movement(1, "Symphony No. 5, Op. 67", "Allegro", 1),
            movement(2, "symphony no 5 op67", "Adagio", 2)
        )
        groupWorks(songs).size shouldBe 1
        val other = listOf(movement(1, "Symphony No. 5", "Allegro", 1), movement(2, "Symphony No. 5", "Adagio", 2, composer = "Mahler"))
        groupWorks(other).size shouldBe 2
    }

    @Test
    fun `a block spans a disc break and records it`() {
        val songs = listOf(
            movement(1, "Mass in B minor, BWV 232", "Kyrie", 1, disc = 1),
            movement(2, "Mass in B minor, BWV 232", "Gloria", 2, disc = 1),
            movement(3, "Mass in B minor, BWV 232", "Credo", 3, disc = 2),
            movement(4, "Mass in B minor, BWV 232", "Sanctus", 4, disc = 2)
        )
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.discBreaks shouldBe listOf(DiscBreak(2, 2))
        block.title.catalogue shouldBe "BWV 232"
        block.title.key shouldBe null
        block.title.core shouldBe "Mass in B minor"
    }

    @Test
    fun `a work is inferred from a title prefix when there is no work tag`() {
        val songs = listOf(
            movement(1, null, "Symphony No. 5: I. Allegro", 1),
            movement(2, null, "Symphony No. 5: II. Andante", 2),
            movement(3, null, "Other piece", 3)
        )
        val block = (groupWorks(songs).first() as AlbumItem.Work).block
        block.inferred shouldBe true
        block.work shouldBe "Symphony No. 5"
        block.movements.map { it.label } shouldBe listOf("I. Allegro", "II. Andante")
    }

    @Test
    fun `no work is inferred without a composer`() {
        val songs = listOf("Band: One", "Band: Two").mapIndexed { i, n -> song(id = i + 1L, name = n, albumArtist = "Band") }
        groupWorks(songs).all { it is AlbumItem.Track } shouldBe true
    }

    private fun composerless(genre: String?, vararg names: String) = names.mapIndexed { i, n ->
        song(id = i + 1L, name = n, albumArtist = "Artist", artists = listOf("Artist"), album = "Record", genres = listOfNotNull(genre))
    }

    @Test
    fun `an indie Op prefix without a form word stays ungrouped`() {
        groupWorks(composerless(null, "Op. 1: Intro", "Op. 1: Outro")).all { it is AlbumItem.Track } shouldBe true
        groupWorks(composerless("Classical", "Op. 1: Intro", "Op. 1: Outro")).all { it is AlbumItem.Track } shouldBe false
    }

    @Test
    fun `a prog symphony without a catalogue number stays ungrouped`() {
        val songs = composerless("Progressive Rock", "Symphony No. 2 - Pt 1", "Symphony No. 2 - Pt 2")
        groupWorks(songs).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `a form word with a catalogue number groups without a composer or genre`() {
        val songs = composerless(null, "Cello Suite No. 1, BWV 1007: I. Prélude", "Cello Suite No. 1, BWV 1007: II. Allemande")
        (groupWorks(songs).single() as AlbumItem.Work).block.movementCount shouldBe 2
    }

    private fun popSongs(vararg names: String) = names.mapIndexed { i, n ->
        song(id = i + 1L, name = n, albumArtist = "Artist", artists = listOf("Artist"), album = "Record", genres = listOf("Pop")).copy(composer = "Writer")
    }

    @Test
    fun `a pop album with one composer tag stays ungrouped`() {
        groupWorks(popSongs("Artist - One", "Artist - Two")).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `songs sharing a title prefix are not a work without classical signs`() {
        val songs = popSongs("Hold On - Live", "Hold On - Acoustic")
        groupWorks(songs).all { it is AlbumItem.Track } shouldBe true
    }

    private fun withComposer(composer: String, albumArtist: String, album: String, genres: List<String>, vararg names: String) = names.mapIndexed { i, n ->
        song(id = i + 1L, name = n, albumArtist = albumArtist, artists = listOf(albumArtist), album = album, genres = genres).copy(composer = composer)
    }

    @Test
    fun `a soundtrack with a form word in its title prefix stays ungrouped`() {
        val massEffect = withComposer("Clint Mansell", "Various Artists", "Mass Effect 3 OST", emptyList(), "Mass Effect - Leaving Earth", "Mass Effect - An End Once and For All")
        groupWorks(massEffect).all { it is AlbumItem.Track } shouldBe true
        val requiem = withComposer("Clint Mansell", "Various Artists", "Requiem", listOf("Soundtrack"), "Requiem for a Dream - Summer", "Requiem for a Dream - Winter")
        groupWorks(requiem).all { it is AlbumItem.Track } shouldBe true
        val hogwarts = withComposer("John Williams", "Various Artists", "Harry Potter", listOf("Soundtrack; Orchestral"), "Hogwarts: A", "Hogwarts: B")
        groupWorks(hogwarts).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `a form word alone is not enough but a catalogue number or a credit is`() {
        val bare = withComposer("Mahler", "Orchestra", "Disc", emptyList(), "Symphony - I", "Symphony - II")
        groupWorks(bare).all { it is AlbumItem.Track } shouldBe true
        val catalogue = withComposer("Bach", "Orchestra", "Disc", emptyList(), "Mass, BWV 232: Kyrie", "Mass, BWV 232: Gloria")
        groupWorks(catalogue).single() shouldBe groupWorks(catalogue).first()
        (groupWorks(catalogue).single() is AlbumItem.Work) shouldBe true
        val credited = withComposer("Mahler", "Orchestra", "Disc", emptyList(), "Symphony - I", "Symphony - II").map { it.copy(conductor = "Abbado") }
        (groupWorks(credited).single() is AlbumItem.Work) shouldBe true
    }

    @Test
    fun `an album artist who is the composer still groups with a classical signal`() {
        val mahler = withComposer("Mahler", "Mahler", "Disc", listOf("Classical"), "Symphony No. 5: I. Trauermarsch", "Symphony No. 5: II. Stürmisch bewegt")
        (groupWorks(mahler).single() is AlbumItem.Work) shouldBe true
        val pop = withComposer("Artist", "Artist", "Disc", listOf("Pop"), "Symphony No. 5: One", "Symphony No. 5: Two")
        groupWorks(pop).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `a pop queue has no lock screen text work progress or work run`() {
        val queue = popSongs("Hold On - Live", "Hold On - Acoustic")
        lockScreenText(queue, 0) shouldBe null
        workProgress(queue, 0) shouldBe null
        workRunAt(queue, 0) shouldBe 0..0
        workRunAt(queue, 1) shouldBe 1..1
    }

    @Test
    fun `an untagged CD rip groups by its work prefix`() {
        val songs = listOf("II. Andante con moto", "III. Allegro").mapIndexed { i, n ->
            song(id = i + 1L, name = "Symphony No. 5 in C minor, Op. 67: $n", albumArtist = "Orchestra", artists = listOf("Orchestra"), album = "Disc")
                .copy(composer = "Beethoven")
        }
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.inferred shouldBe true
        block.movements.map { it.name } shouldBe listOf("Andante con moto", "Allegro")
    }

    @Test
    fun `a queue starting at movement 3 keeps its numerals`() {
        val queue = symphony().drop(2).mapIndexed { i, s -> s.copy(movementNumber = i + 3, movementTotal = 4) }
        lockScreenText(queue, 0)!!.title shouldBe "III. Scherzo"
        lockScreenText(queue, 1)!!.title shouldBe "IV. Allegro"
    }

    @Test
    fun `a repeated tagged movement number keeps its numeral`() {
        val songs = listOf(
            movement(1, "Symphony No. 5", "Allegro", 1).copy(movementNumber = 2, movementTotal = 4),
            movement(2, "Symphony No. 5", "Andante", 2).copy(movementNumber = 2, movementTotal = 4)
        )
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.movements.map { it.numeral } shouldBe listOf("II", "II")
    }

    @Test
    fun `a lone movement keeps its numeral`() {
        val queue = listOf(movement(1, "Symphony No. 5", "Andante", 2).copy(movementNumber = 2, movementTotal = 4))
        lockScreenText(queue, 0)!!.title shouldBe "II. Andante"
    }

    @Test
    fun `a slice of a 24 song cycle is numbered like the whole`() {
        val cycle = List(24) {
            song(id = it + 1L, name = "Lied ${it + 1}", albumArtist = "Fischer-Dieskau", genres = listOf("Classical"))
                .copy(composer = "Schubert", work = "Winterreise", conductor = "Moore", movementNumber = it + 1, movementTotal = 24)
        }
        val album = (groupWorks(cycle).single() as AlbumItem.Work).block
        val slice = cycle.subList(4, 6)
        lockScreenText(slice, 0)!!.title shouldBe album.movements[4].label
        lockScreenText(slice, 0)!!.title shouldBe "5. Lied 5"
    }

    @Test
    fun `Atom Heart Mother numbers its parts`() {
        val songs = listOf("Father's Shout", "Breast Milky", "Mother Fore").mapIndexed { i, n ->
            song(
                id = i + 1L,
                name = "Atom Heart Mother: ${if (i == 0) {
                    "I"
                } else if (i == 1) {
                    "II"
                } else {
                    "III"
                }}. $n",
                albumArtist = "Pink Floyd",
                genres = listOf("Rock")
            )
                .copy(composer = "Pink Floyd", work = "Atom Heart Mother")
        }
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.numbering shouldBe WorkNumbering(NumeralStyle.Arabic, "parts")
        block.movements[1].label shouldBe "2. Breast Milky"
    }

    @Test
    fun `Winterreise is a block of 24 songs`() {
        val songs = List(24) {
            song(id = it + 1L, name = "Lied ${it + 1}", albumArtist = "Fischer-Dieskau", genres = listOf("Lieder"))
                .copy(composer = "Schubert", work = "Winterreise, D. 911", performers = listOf(Performer("Fischer-Dieskau", "baritone")))
        }
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.movementCount shouldBe 24
        block.numbering.unit shouldBe "songs"
        block.title.catalogue shouldBe "D. 911"
        block.collapsedByDefault shouldBe false
    }

    @Test
    fun `Winterreise collapses beside another work`() {
        val cycle = List(24) {
            song(id = it + 1L, name = "Lied ${it + 1}", albumArtist = "Fischer-Dieskau", genres = listOf("Lieder"))
                .copy(composer = "Schubert", work = "Winterreise, D. 911", performers = listOf(Performer("Fischer-Dieskau", "baritone")))
        }
        val other = List(3) { movement(100L + it, "Symphony No. 5", "Allegro", it + 1, composer = "Beethoven") }
        val items = groupWorks(cycle + other)
        (items[0] as AlbumItem.Work).block.collapsedByDefault shouldBe true
        (items[1] as AlbumItem.Work).block.collapsedByDefault shouldBe false
    }

    @Test
    fun `a long block collapses only when the album has several`() {
        val long = List(21) { movement(it + 1L, "Goldberg Variations", "Aria", it + 1) }
        (groupWorks(long).single() as AlbumItem.Work).block.collapsedByDefault shouldBe false
        val two = groupWorks(long + symphony(startId = 100))
        (two[0] as AlbumItem.Work).block.collapsedByDefault shouldBe true
        (two[1] as AlbumItem.Work).block.collapsedByDefault shouldBe false
    }

    @Test
    fun `variation names keep their own label`() {
        val songs = listOf("Aria", "Var. 1", "Var. 13").mapIndexed { i, n -> movement(i + 1L, "Goldberg Variations", n, i + 1) }
        (groupWorks(songs).single() as AlbumItem.Work).block.movements.map { it.numeral } shouldBe listOf(null, null, null)
    }

    @Test
    fun `act prefixes split a block into sections`() {
        val names = listOf("Overture", "Act 1: Scene 1", "Act 1: Scene 2", "Act 2: Scene 1")
        val songs = names.mapIndexed { i, n -> movement(i + 1L, "Die Zauberflöte", n, i + 1, composer = "Mozart") }
        val block = (groupWorks(songs).single() as AlbumItem.Work).block
        block.acts shouldBe listOf(ActSection(null, 0..0), ActSection("Act 1", 1..2), ActSection("Act 2", 3..3))
    }

    @Test
    fun `a work without acts has no sections`() {
        (groupWorks(symphony()).single() as AlbumItem.Work).block.acts shouldBe emptyList()
    }

    @Test
    fun `a guest conductor shows credits only on that work`() {
        val guest = symphony(startId = 10, work = "Symphony No. 7").map { it.copy(conductor = "Karajan") }
        val items = groupWorks(symphony() + symphony(startId = 5) + guest)
        (items.last() as AlbumItem.Work).block.credits shouldBe "Karajan"
        (items.first() as AlbumItem.Work).block.credits shouldBe null
    }

    @Test
    fun `lock screen text uses the movement label and the work line`() {
        val queue = symphony()
        lockScreenText(queue, 1) shouldBe LockScreenText("II. Andante con moto", "Beethoven: Symphony No. 5 · Kleiber")
    }

    @Test
    fun `lock screen text for a lone classical song uses the cleaned name`() {
        val queue = listOf(movement(1, "Moonlight Sonata", "Adagio", 1))
        lockScreenText(queue, 0) shouldBe LockScreenText("Adagio", "Beethoven: Moonlight Sonata · Kleiber")
    }

    @Test
    fun `lock screen text is absent for ordinary songs`() {
        lockScreenText(listOf(song(id = 1, name = "Hello")), 0) shouldBe null
    }

    @Test
    fun `work progress counts remaining time`() {
        val queue = symphony()
        val progress = workProgress(queue, 1, positionMs = 20_000)!!
        progress.segments shouldBe listOf(60_000L, 60_000L, 60_000L, 60_000L)
        progress.currentSegment shouldBe 1
        progress.movementIndex shouldBe 1
        progress.movementCount shouldBe 4
        progress.remainingMs shouldBe 160_000L
        progress.continuous shouldBe false
    }

    @Test
    fun `work progress is offset by the work start in the queue`() {
        val queue = listOf(movement(99, null, "Intro", 1)) + symphony()
        workProgress(queue, 3)!!.segmentStarts shouldBe listOf(1, 2, 3, 4)
        workProgress(queue, 0) shouldBe null
    }

    @Test
    fun `work progress uses acts as segments`() {
        val names = listOf("Overture", "Act 1: Scene 1", "Act 1: Scene 2", "Act 2: Scene 1")
        val queue = names.mapIndexed { i, n -> movement(i + 1L, "Die Zauberflöte", n, i + 1, composer = "Mozart") }
        val progress = workProgress(queue, 2)!!
        progress.segments shouldBe listOf(60_000L, 120_000L, 60_000L)
        progress.segmentStarts shouldBe listOf(0, 1, 3)
        progress.currentSegment shouldBe 1
    }

    @Test
    fun `work progress is continuous above twelve segments`() {
        workProgress(List(12) { movement(it + 1L, "Suite", "Piece", it + 1) }, 0)!!.continuous shouldBe false
        workProgress(List(13) { movement(it + 1L, "Suite", "Piece", it + 1) }, 0)!!.continuous shouldBe true
    }

    @Test
    fun `workRunAt returns the whole work`() {
        val queue = listOf(movement(99, null, "Intro", 1)) + symphony()
        workRunAt(queue, 3) shouldBe 1..4
        workRunAt(queue, 0) shouldBe 0..0
    }
}
