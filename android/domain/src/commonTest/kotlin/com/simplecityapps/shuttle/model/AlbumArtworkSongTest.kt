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
    fun `an empty album has no song`() {
        emptyList<Song>().albumArtworkSong() shouldBe null
    }
}
