package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlbumArtworkSongTest {
    @Test
    fun `the first song with an image version stands in for the album`() {
        val bare = song(id = 1)
        val withImage = song(id = 2).copy(artworkVersion = "v1")
        val alsoWithImage = song(id = 3).copy(artworkVersion = "v2")

        listOf(bare, withImage, alsoWithImage).albumArtworkSong() shouldBe withImage
    }

    @Test
    fun `with no image versions the first song stands in`() {
        val first = song(id = 1)

        listOf(first, song(id = 2)).albumArtworkSong() shouldBe first
    }

    @Test
    fun `the song is the same whatever order the list is in`() {
        val first = song(id = 1, track = 1, path = "/a.mp3").copy(artworkVersion = "v1")
        val second = song(id = 2, track = 2, path = "/b.mp3").copy(artworkVersion = "v2")

        listOf(second, first).albumArtworkSong() shouldBe first
        listOf(first, second).albumArtworkSong() shouldBe first
    }

    @Test
    fun `an empty album has no song`() {
        emptyList<Song>().albumArtworkSong() shouldBe null
    }
}
