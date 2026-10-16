package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Tags as Picard wrote them on real albums, so grouping is checked against what libraries actually contain.
class RealAlbumTagsTest {
    private fun kleiber(): List<Song> {
        val tracks = listOf(
            "Symphonie nr. 5 c‐Moll, op. 67: I. Allegro con brio" to "Symphony no. 5 in C minor, op. 67: I. Allegro con brio",
            "Symphonie nr. 5 c‐Moll, op. 67: II. Andante con moto" to "Symphony no. 5 in C minor, op. 67: II. Andante con moto",
            "Symphonie nr. 5 c‐Moll, op. 67: III. Allegro" to "Symphony no. 5 in C minor, op. 67: III. Scherzo. Allegro",
            "Symphonie nr. 5 c‐Moll, op. 67: IV. Allegro" to "Symphony no. 5 in C minor, op. 67: IV. Allegro",
            "Symphony nr. 7 A‐Dur, op. 92: I. Poco sostenuto – Vivace" to "Symphony no. 7 in A major, op. 92: I. Poco sostenuto - Vivace",
            "Symphony nr. 7 A‐Dur, op. 92: II. Allegretto" to "Symphony no. 7 in A major, op. 92: II. Allegretto",
            "Symphony nr. 7 A‐Dur, op. 92: III. Presto" to "Symphony no. 7 in A major, op. 92: III. Presto",
            "Symphony nr. 7 A‐Dur, op. 92: IV. Allegro con brio" to "Symphony no. 7 in A major, op. 92: IV. Allegro con brio"
        )
        return tracks.mapIndexed { index, (title, work) ->
            song(
                id = index + 1L,
                name = title,
                albumArtist = "Ludwig van Beethoven; Wiener Philharmoniker, Carlos Kleiber",
                artists = listOf("Ludwig van Beethoven"),
                album = "Symphonies 5 & 7",
                track = index + 1,
                genres = listOf("Classical")
            ).copy(
                composer = "Ludwig van Beethoven",
                composerSort = "Beethoven, Ludwig van",
                conductor = "Carlos Kleiber",
                performers = listOf(Performer("Wiener Philharmoniker", "orchestra")),
                work = work
            )
        }
    }

    private fun winterreise(): List<Song> {
        val names = listOf(
            "Gute Nacht", "Die Wetterfahne", "Gefrorne Tränen", "Erstarrung", "Der Lindenbaum", "Wasserflut", "Auf dem Flusse", "Rückblick",
            "Irrlicht", "Rast", "Frühlingstraum", "Einsamkeit", "Die Post", "Der greise Kopf", "Die Krähe", "Letzte Hoffnung", "Im Dorfe",
            "Der stürmische Morgen", "Täuschung", "Der Wegweiser", "Das Wirtshaus", "Mut", "Die Nebensonnen", "Der Leiermann"
        )
        val romans = listOf(
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI",
            "XXII", "XXIII", "XXIV"
        )
        return names.mapIndexed { index, name ->
            song(
                id = index + 1L,
                name = "Winterreise, D. 911: ${index + 1}. $name${if (name == "Mut") "!" else ""}",
                albumArtist = "Franz Schubert; Dietrich Fischer‐Dieskau, Gerald Moore",
                artists = listOf("Franz Schubert"),
                album = "Winterreise",
                track = index + 1,
                genres = listOf("Classical")
            ).copy(
                composer = "Franz Schubert",
                composerSort = "Schubert, Franz",
                performers = listOf(Performer("Gerald Moore", "piano"), Performer("Dietrich Fischer‐Dieskau", "baritone vocals")),
                work = "Die Winterreise, op. 89, D. 911: ${romans[index]}. $name"
            )
        }
    }

    private fun yoYoMa(): List<Song> {
        val suites = listOf(
            "Unaccompanied Cello Suite No. 1 in G Major, BWV 1007",
            "Unaccompanied Cello Suite No. 2 in D minor, BWV 1008",
            "Unaccompanied Cello Suite No. 3 in C Major, BWV 1009",
            "Unaccompanied Cello Suite No. 4 in E-flat Major, BWV 1010",
            "Unaccompanied Cello Suite No. 5 in C minor, BWV 1011",
            "Unaccompanied Cello Suite No. 6 in D Major, BWV 1012"
        )
        val movements = listOf("I. Prélude", "II. Allemande", "III. Courante", "IV. Sarabande", "V. Menuets I & II", "VI. Gigue")
        return suites.flatMapIndexed { suite, title ->
            movements.mapIndexed { index, movement ->
                song(
                    id = suite * 6L + index + 1,
                    name = "$title : $movement",
                    albumArtist = "Yo‐Yo Ma",
                    artists = listOf("Yo‐Yo Ma"),
                    album = "Six Evolutions: Bach Cello Suites",
                    track = suite * 6 + index + 1,
                    genres = listOf("Baroque", "Classical")
                )
            }
        }
    }

    private fun stokowski(): List<Song> {
        val titles = listOf(
            "Toccata and Fugue in D minor, BWV 565: Toccata",
            "Toccata and Fugue in D minor, BWV 565: Fugue",
            "Aria (Air on the G String) from Orchestral Suite no. 3, BWV 1068",
            "Fugue in G minor, BWV 578 \"Little Fugue\"",
            "Sheep May Safely Graze from Cantata no. 208",
            "Prelude in B minor from BWV 869",
            "Passacaglia and Fugue in C minor, BWV 582: Passacaglia",
            "Passacaglia and Fugue in C minor, BWV 582: Fugue",
            "Siciliano from BWV 1017",
            "Mein Jesu, BWV 487",
            "Chorale from the Easter Cantata, BWV 4",
            "Adagio in C from BWV 564",
            "Komm süsser Tod, BWV 478",
            "Wir glauben all' an Einen Gott, BWV 680 \"Giant Fugue\""
        )
        return titles.mapIndexed { index, title ->
            song(
                id = index + 1L,
                name = title,
                albumArtist = "Johann Sebastian Bach",
                artists = listOf("Johann Sebastian Bach"),
                album = "Stokowski's Symphonic Bach",
                track = index + 1,
                genres = listOf("Baroque", "Orchestral", "Cantata", "Motet", "Mass", "Classical")
            )
        }
    }

    private fun atomHeartMother(): List<Song> {
        val titles = listOf(
            "Atom Heart Mother: a) Father’s Shout / b) Breast Milky / c) Mother Fore",
            "If",
            "Summer ’68",
            "Fat Old Sun",
            "Alan’s Psychedelic Breakfast: a) Rise and Shine / b) Sunny Side Up / c) Morning Glory"
        )
        return titles.mapIndexed { index, title ->
            song(id = index + 1L, name = title, albumArtist = "Pink Floyd", artists = listOf("Pink Floyd"), album = "Atom Heart Mother", track = index + 1)
        }
    }

    private fun mahler(albumArtist: String = "Mahler; Arleen Augér, Janet Baker, CBSO Chorus, City of Birmingham Symphony Orchestra, Simon Rattle"): List<Song> {
        val movements = listOf(
            "I. Allegro maestoso", "II. Andante moderato", "III. In ruhig fliessender Bewegung", "IV. Urlicht. Sehr feierlich", "V. Im Tempo des Scherzo. Wild herausfahrend",
            "V. Wieder sehr breit", "V. Ritardando… Maestoso", "V. Wieder zurückhaltend", "V. Langsam. Misterioso", "V. Etwas bewegter", "V. Mit Aufschwung aber nicht eilen"
        )
        return movements.mapIndexed { index, movement ->
            song(
                id = index + 1L,
                name = "Symphony no. 2 in C minor “Resurrection”: $movement",
                albumArtist = albumArtist,
                artists = listOf("Gustav Mahler"),
                album = "Symphony No. 2",
                track = if (index == 0) 1 else index,
                disc = if (index == 0) 1 else 2,
                genres = listOf("Classical")
            ).copy(composer = if (index == 0) "Mahler" else "Mahler, Gustav (1860 - 1911)")
        }
    }

    private fun blocks(songs: List<Song>) = groupWorks(songs).map { (it as AlbumItem.Work).block }

    @Test
    fun `Kleiber is two symphonies of four movements`() {
        val blocks = blocks(kleiber())
        blocks.map { it.movementCount } shouldBe listOf(4, 4)
        blocks.map { it.title.core } shouldBe listOf("Symphony No. 5", "Symphony No. 7")
        blocks.forEach { it.numbering shouldBe WorkNumbering(NumeralStyle.Roman, "movements") }
        blocks.first().movements.map { it.label } shouldBe listOf("I. Allegro con brio", "II. Andante con moto", "III. Scherzo. Allegro", "IV. Allegro")
        blocks.last().movements.map { it.numeral } shouldBe listOf("I", "II", "III", "IV")
    }

    @Test
    fun `Yo-Yo Ma is six suites of six movements without a composer tag`() {
        val blocks = blocks(yoYoMa())
        blocks.map { it.movementCount } shouldBe List(6) { 6 }
        blocks.first().title.core shouldBe "Unaccompanied Cello Suite No. 1"
        blocks.first().movements.map { it.name }.take(2) shouldBe listOf("Prélude", "Allemande")
    }

    @Test
    fun `Stokowski groups only the Toccata and Passacaglia pairs`() {
        val items = groupWorks(stokowski())
        items.map { if (it is AlbumItem.Work) it.block.movementCount else 1 } shouldBe listOf(2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1)
        items.filterIsInstance<AlbumItem.Work>().map { it.block.title.core } shouldBe listOf("Toccata and Fugue in D minor", "Passacaglia and Fugue in C minor")
    }

    @Test
    fun `Mahler is one symphony whatever the composer spelling`() {
        val blocks = blocks(mahler())
        blocks.map { it.movementCount } shouldBe listOf(11)
        blocks.single().title.core shouldBe "Symphony No. 2"
        blocks.single().movements.map { it.numeral } shouldBe listOf("I", "II", "III", "IV", "V") + List(6) { null }
        blocks.single().movements.map { it.number } shouldBe listOf(1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5)
    }

    @Test
    fun `Mahler is one symphony of 11 across both discs when the album artist is the full name`() {
        val blocks = blocks(mahler(albumArtist = "Gustav Mahler"))
        blocks.map { it.movementCount } shouldBe listOf(11)
    }

    @Test
    fun `Mahler credits come from the album artist when no conductor or performer is tagged`() {
        val songs = mahler()
        val expected = "Arleen Augér · Janet Baker · CBSO Chorus · City of Birmingham Symphony Orchestra · Simon Rattle"
        albumCreditsLine(songs) shouldBe expected
        nowPlayingWork(songs, 2)!!.performers shouldBe expected
        nowPlayingWork(songs, 2)!!.miniSubtitle shouldBe "III. In ruhig fliessender Bewegung · Arleen Augér"
    }

    @Test
    fun `an artist tag other than the composer is the credit when the album artist is the composer`() {
        val songs = mahler(albumArtist = "Gustav Mahler").map { it.copy(artists = listOf("Gustav Mahler", "Simon Rattle")) }
        albumCreditsLine(songs) shouldBe "Simon Rattle"
    }

    @Test
    fun `Yo-Yo Ma numerals come from the titles`() {
        blocks(yoYoMa()).first().movements.map { it.numeral } shouldBe listOf("I", "II", "III", "IV", "V", "VI")
    }

    @Test
    fun `composer display name reads a surname first name without a sort tag`() {
        composerDisplayName(ComposerName("Mahler, Gustav (1860 - 1911)", null)) shouldBe "Mahler"
        composerDisplayName(ComposerName("Mahler", null)) shouldBe "Mahler"
        composerDisplayName(ComposerName("Gustav Mahler", null)) shouldBe "Gustav Mahler"
    }

    @Test
    fun `Hahn stays ungrouped because nothing names a work`() {
        val titles = listOf(
            "Allegro Molto Appassionato",
            "Andante",
            "Allegretto Non Troppo - Allegro Molto Vivace",
            "Nocturne. Moderato",
            "Scherzo. Allegro",
            "Passacaglia. Andante",
            "Cadenza",
            "Burlesque. Allegro Con Brio"
        )
        val songs = titles.mapIndexed { index, title ->
            song(id = index + 1L, name = title, albumArtist = null, artists = listOf("Hilary Hahn"), album = "Mendelssohn & Shostakovich Violin Concertos", track = index + 1, genres = listOf("Classical"))
        }
        groupWorks(songs).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `Atom Heart Mother stays five lone tracks`() {
        groupWorks(atomHeartMother()).all { it is AlbumItem.Track } shouldBe true
    }

    @Test
    fun `Winterreise is one work of 24 songs`() {
        val blocks = blocks(winterreise())
        blocks.map { it.movementCount } shouldBe listOf(24)
        blocks.single().title.core shouldBe "Die Winterreise"
        blocks.single().title.catalogue shouldBe "D. 911"
        blocks.single().numbering shouldBe WorkNumbering(NumeralStyle.Arabic, "songs")
        blocks.single().movements.map { it.label }.let { it.first() to it.last() } shouldBe ("1. Gute Nacht" to "24. Der Leiermann")
        blocks.single().movements.map { it.number } shouldBe (1..24).toList()
    }
}
