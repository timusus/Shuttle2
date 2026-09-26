package com.simplecityapps.imageloading.coil.source

import io.kotest.matchers.shouldBe
import org.junit.Test

/** #169: `albumart.jpg` didn't show as cover art; renaming it to `album.jpg` worked around it. */
class FolderArtworkSourceTest {
    @Test
    fun `albumart matches regardless of extension, reproducing 169`() {
        matchesFolderCoverName("albumart.jpg") shouldBe true
        matchesFolderCoverName("albumart.png") shouldBe true
    }

    @Test
    fun `every recognised cover name and extension matches, any case`() {
        listOf("folder", "cover", "album", "albumart", "front", "artwork").forEach { name ->
            listOf("jpg", "jpeg", "png", "webp").forEach { extension ->
                matchesFolderCoverName("$name.$extension") shouldBe true
                matchesFolderCoverName("$name.$extension".uppercase()) shouldBe true
                matchesFolderCoverName(".$name.$extension") shouldBe true
            }
        }
    }

    @Test
    fun `an unrelated file name does not match`() {
        matchesFolderCoverName("song.mp3") shouldBe false
        matchesFolderCoverName("artist.jpg") shouldBe false
        matchesFolderCoverName("albumart.gif") shouldBe false
    }
}
