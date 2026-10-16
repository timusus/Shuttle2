package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClassicalSignalTest {
    private fun twelveTrackRockAlbum(configure: (Song) -> Song = { it }) = (1..12).map { n ->
        configure(song(id = n.toLong(), name = "Track $n", track = n, album = "Greatest Hits", genres = listOf("Rock")))
    }

    @Test
    fun `a grouping tag on a rock album does not make it classical`() {
        val songs = twelveTrackRockAlbum { it.copy(work = "Favourites") }

        songs.isClassicalBlock() shouldBe false
        groupWorks(songs).none { it is AlbumItem.Work } shouldBe true
        workRunOf(songs, 3) shouldBe null
        workProgress(songs, 3) shouldBe null
        lockScreenText(songs, 3) shouldBe null
    }

    @Test
    fun `a movement tag on a rock track does not relabel it`() {
        val rock = song(id = 1, name = "Time", track = 4, genres = listOf("Rock")).copy(movementNumber = 2)
        val classical = song(id = 2, name = "Allegro", track = 1).copy(movementNumber = 2)

        rock.showsMovementLabel shouldBe false
        classical.showsMovementLabel shouldBe true
    }

    @Test
    fun `a pop title with a work tag keeps its title`() {
        val queen = song(id = 1, name = "Bohemian Rhapsody - Remastered 2011", track = 11)
            .copy(work = "Bohemian Rhapsody", composer = "Freddie Mercury", genres = listOf("Rock"))
        val next = song(id = 2, name = "Love of My Life - Remastered 2011", track = 12).copy(genres = listOf("Rock"))

        listOf(queen, next).isClassicalBlock() shouldBe false
        workRunOf(listOf(queen, next), 0) shouldBe null
        queen.isClassical shouldBe false
        queen.classicalText(artist = "Queen", album = "Greatest Hits") shouldBe null
        lockScreenText(listOf(queen, next), 0) shouldBe null
    }

    @Test
    fun `a dance-pop remix EP with composer and work tags is not classical`() {
        val songs = listOf("Mambo No. 5 (Radio Edit)", "Mambo No. 5 (Extended)").mapIndexed { i, title ->
            song(id = i + 1L, name = title, track = i + 1, album = "Mambo No. 5 Remixes")
                .copy(work = "Mambo No. 5", composer = "Damaso Perez Prado", genres = listOf("Dance-Pop"))
        }

        songs.isClassicalBlock() shouldBe false
        groupWorks(songs).none { it is AlbumItem.Work } shouldBe true
    }

    @Test
    fun `performer credits on a rock album are not classical performers`() {
        val songs = twelveTrackRockAlbum {
            it.copy(performers = listOf(Performer(name = "Jane Doe", role = "guitar"), Performer(name = "John Roe", role = "drums")))
        }

        songs.isClassicalBlock() shouldBe false
        groupWorks(songs).none { it is AlbumItem.Work } shouldBe true
    }

    @Test
    fun `a conductor or a classical genre or a catalogue key is a classical signal`() {
        listOf(song(id = 1).copy(conductor = "Kleiber")).isClassicalBlock() shouldBe true
        listOf(song(id = 1).copy(genres = listOf("Chamber Music"))).isClassicalBlock() shouldBe true
        listOf(song(id = 1).copy(work = "Piano Sonata, Op. 31 No. 2")).isClassicalBlock() shouldBe true
        listOf(song(id = 1).copy(work = "Suite", composer = "Someone")).isClassicalBlock() shouldBe false
    }

    @Test
    fun `a popular genre vetoes a classical signal`() {
        listOf(song(id = 1).copy(conductor = "Someone", genres = listOf("Orchestral Pop"))).isClassicalBlock() shouldBe false
    }

    @Test
    fun `genres match as whole words`() {
        listOf("Popular Classical", "Electroacoustic", "Latin Mass", "Soulful choral").forEach {
            hasNonClassicalGenre(listOf(it)) shouldBe false
        }
        listOf("Popular Classical", "Latin Mass", "Soulful choral").forEach { hasClassicalGenre(listOf(it)) shouldBe true }
        listOf("Space Opera", "Rock Opera", "Alternative Rock", "Synthpop", "Hip-Hop", "Dance-Pop").forEach {
            hasClassicalGenre(listOf(it)) shouldBe false
        }
        listOf("Rock Opera", "Alternative Rock", "Synthpop", "Hip-Hop", "Dance-Pop").forEach {
            hasNonClassicalGenre(listOf(it)) shouldBe true
        }
    }

    @Test
    fun `any popular word vetoes a classical word`() {
        listOf("Chamber Folk", "Chamber Jazz", "Symphonic Jazz", "Folk Opera", "Orchestral Electronic", "Soul Choral", "Orchestral Gospel").forEach {
            hasClassicalGenre(listOf(it)) shouldBe false
            hasNonClassicalGenre(listOf(it)) shouldBe true
        }
        hasClassicalGenre(listOf("Latin Mass")) shouldBe true
        hasNonClassicalGenre(listOf("Latin Mass")) shouldBe false
        hasNonClassicalGenre(listOf("Latin")) shouldBe true
    }

    @Test
    fun `popular roots match as word prefixes`() {
        listOf("Dancehall", "Metalcore", "Rockabilly", "Folktronica", "Indietronica", "Electroclash", "Rocksteady", "Technopop", "Poppunk").forEach {
            hasNonClassicalGenre(listOf(it)) shouldBe true
        }
        listOf("Popular", "Electroacoustic", "Soulful choral").forEach { hasNonClassicalGenre(listOf(it)) shouldBe false }
    }

    @Test
    fun `operetta is classical but space and rock opera are not`() {
        hasClassicalGenre(listOf("Operetta")) shouldBe true
        hasClassicalGenre(listOf("Opera")) shouldBe true
        hasClassicalGenre(listOf("Space Opera")) shouldBe false
        hasClassicalGenre(listOf("Rock Opera")) shouldBe false
    }

    @Test
    fun `a movement number on a rock song gives plain lock screen text`() {
        val rock = song(id = 1, name = "Time", track = 4, genres = listOf("Rock")).copy(movementNumber = 2)

        lockScreenText(listOf(rock), 0) shouldBe null
    }
}
