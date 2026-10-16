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

    private fun blocks(songs: List<Song>) = groupWorks(songs).map { (it as AlbumItem.Work).block }

    @Test
    fun `Kleiber is two symphonies of four movements`() {
        val blocks = blocks(kleiber())
        blocks.map { it.movementCount } shouldBe listOf(4, 4)
        blocks.map { it.title.core } shouldBe listOf("Symphony no. 5", "Symphony no. 7")
    }

    @Test
    fun `Winterreise is one work of 24 songs`() {
        val blocks = blocks(winterreise())
        blocks.map { it.movementCount } shouldBe listOf(24)
        blocks.single().title.core shouldBe "Die Winterreise"
        blocks.single().title.catalogue shouldBe "D. 911"
    }
}
