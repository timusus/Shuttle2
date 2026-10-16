package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ComposerWorksTest {
    private var nextId = 1L

    private fun movements(
        album: String,
        work: String,
        composer: String = "Beethoven",
        conductor: String = "Kleiber",
        genres: List<String> = listOf("Classical"),
        count: Int = 2,
    ) = List(count) {
        song(id = nextId++, name = "Movement ${it + 1}", albumArtist = conductor, album = album, track = it + 1, genres = genres)
            .copy(composer = composer, conductor = conductor, work = work)
    }

    private val find = FindComposerWorks()

    @Test
    fun `recordings of one work across albums are one row`() {
        val library = movements("Kleiber 5", "Symphony No. 5 in C minor, Op. 67") +
            movements("Karajan 5", "Symphony no. 5 in C minor, op. 67", conductor = "Karajan") +
            movements("Kleiber 7", "Symphony No. 7 in A, Op. 92")
        val result = find("Beethoven", library)!!
        result.works.map { it.recordings.size } shouldBe listOf(2, 1)
        result.albums.size shouldBe 3
    }

    @Test
    fun `works are listed alphabetically with their catalogue number`() {
        val library = movements("B", "Symphony No. 7 in A, Op. 92") + movements("A", "Symphony No. 5 in C minor, Op. 67")
        find("Beethoven", library)!!.works.map { it.catalogue } shouldBe listOf("Op. 67", "Op. 92")
    }

    @Test
    fun `composer name variants are one composer`() {
        val library = movements("A", "Symphony No. 5, Op. 67", composer = "Beethoven") +
            movements("B", "Symphony No. 7, Op. 92", composer = "Ludwig van Beethoven") +
            movements("C", "Symphony No. 9, Op. 125", composer = "Beethoven, Ludwig van")
        find("Beethoven", library)!!.works.size shouldBe 3
    }

    @Test
    fun `another composer's works are excluded`() {
        val library = movements("A", "Symphony No. 5, Op. 67") + movements("B", "Symphony No. 1, Op. 68", composer = "Brahms")
        find("Beethoven", library)!!.albums.size shouldBe 1
    }

    @Test
    fun `non-classical songs are excluded`() {
        val library = movements("Pop", "Symphony No. 5, Op. 67", genres = listOf("Pop"))
        find("Beethoven", library) shouldBe null
    }

    @Test
    fun `an unknown composer has no page`() {
        find("Nobody", movements("A", "Symphony No. 5, Op. 67")) shouldBe null
    }
}
