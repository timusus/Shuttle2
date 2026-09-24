package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalArtworkVersionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cover = FolderImage(name = "cover.jpg", lastModified = 100, size = 2_000)

    @Test
    fun `version is stable across two identical scans`() {
        localArtworkVersion(1_000, listOf(cover)) shouldBe localArtworkVersion(1_000, listOf(cover.copy()))
    }

    @Test
    fun `version does not depend on the order images are listed in`() {
        val artist = FolderImage(name = "artist.jpg", lastModified = 50, size = 10)

        localArtworkVersion(1_000, listOf(cover, artist)) shouldBe localArtworkVersion(1_000, listOf(artist, cover))
    }

    @Test
    fun `version is the audio file's modified time when there are no folder images`() {
        localArtworkVersion(1_000, emptyList()) shouldBe "1000"
    }

    @Test
    fun `version changes when the audio file is modified, for example new embedded art`() {
        localArtworkVersion(1_000, listOf(cover)) shouldNotBe localArtworkVersion(2_000, listOf(cover))
    }

    @Test
    fun `version changes when a folder image is replaced`() {
        localArtworkVersion(1_000, listOf(cover)) shouldNotBe localArtworkVersion(1_000, listOf(cover.copy(lastModified = 200)))
        localArtworkVersion(1_000, listOf(cover)) shouldNotBe localArtworkVersion(1_000, listOf(cover.copy(size = 3_000)))
    }

    @Test
    fun `version changes when a folder image is added or removed`() {
        localArtworkVersion(1_000, emptyList()) shouldNotBe localArtworkVersion(1_000, listOf(cover))
        localArtworkVersion(1_000, listOf(cover)) shouldNotBe
            localArtworkVersion(1_000, listOf(cover, FolderImage(name = "folder.png", lastModified = 1, size = 1)))
    }

    @Test
    fun `reader lists images in the song's folder and the folder above it, ignoring other files`() {
        val artistFolder = temporaryFolder.newFolder("Artist")
        val albumFolder = File(artistFolder, "Album").apply { mkdirs() }
        File(artistFolder, "artist.png").writeText("a")
        File(albumFolder, "cover.JPG").writeText("bb")
        File(albumFolder, "notes.txt").writeText("ccc")
        val song = File(albumFolder, "song.mp3").apply { writeText("dddd") }

        FolderImageReader().imagesNear(song.path).map { image -> image.name to image.size } shouldContainExactlyInAnyOrder
            listOf("cover.JPG" to 2L, "artist.png" to 1L)
    }

    @Test
    fun `reader reports the image's modified time`() {
        val folder = temporaryFolder.newFolder("Album")
        File(folder, "cover.jpg").apply {
            writeText("a")
            setLastModified(1_234_000)
        }

        FolderImageReader().imagesNear(File(folder, "song.mp3").path).single().lastModified shouldBe 1_234_000
    }
}
