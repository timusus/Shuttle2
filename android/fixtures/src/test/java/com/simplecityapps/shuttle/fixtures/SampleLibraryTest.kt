package com.simplecityapps.shuttle.fixtures

import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test

class SampleLibraryTest {
    @Test
    fun `the manifest describes sixteen albums, one of them a compilation`() {
        SampleLibrary.albums shouldHaveSize 16
        SampleLibrary.albums.count { it.isCompilation } shouldBe 1
        SampleLibrary.artists.size shouldBe 10
    }

    @Test
    fun `every album has a generated cover within the size budget`() {
        SampleLibrary.albums.forEach { album ->
            SampleLibrary.coverBytes(album.id).size shouldBeLessThanOrEqual 60 * 1024
        }
    }

    @Test
    fun `song ids are unique and playlists resolve to library songs`() {
        SampleLibrary.songs.map { it.id }.shouldBeUnique()
        SampleLibrary.playlists.forEach { it.songs.size shouldBeGreaterThan 0 }
    }

    @Test
    fun `a queue alternates albums`() {
        val queue = SampleLibrary.queue(4)
        queue.map { it.albumId } shouldBe SampleLibrary.albums.take(4).map { it.id }
    }

    @Test
    fun `durations format as minutes and seconds`() {
        formatDuration(65) shouldBe "1:05"
        formatDuration(603) shouldBe "10:03"
    }
}
