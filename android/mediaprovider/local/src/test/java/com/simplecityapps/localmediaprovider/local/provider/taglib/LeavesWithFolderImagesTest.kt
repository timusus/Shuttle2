package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.provider.FolderImage
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.DocumentNodeTree
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeavesWithFolderImagesTest {
    private val rootUri = Uri.parse("content://tree/root")

    @Test
    fun `each song is paired with the images in its folder and the folder above it`() {
        val root = tree("Music").apply { imageNodes.add(image("root.jpg", 1)) }
        val artist = tree("Artist").apply { imageNodes.add(image("artist.jpg", 2)) }
        val album = tree("Album").apply { imageNodes.add(image("cover.jpg", 3)) }
        val song = file("song.mp3")
        val artistSong = file("single.mp3")
        root.treeNodes.add(artist)
        artist.treeNodes.add(album)
        artist.leafNodes.add(artistSong)
        album.leafNodes.add(song)

        root.leavesWithFolderImages().toMap() shouldBe
            mapOf(
                song to listOf(FolderImage("cover.jpg", 3, 3), FolderImage("artist.jpg", 2, 2)),
                artistSong to listOf(FolderImage("artist.jpg", 2, 2), FolderImage("root.jpg", 1, 1))
            )
    }

    private fun tree(name: String) = DocumentNodeTree(Uri.parse("content://tree/$name"), rootUri, name, name, "vnd.android.document/directory")

    private fun file(name: String) = DocumentNode(Uri.parse("content://doc/$name"), name, name, "audio/mpeg")

    private fun image(
        name: String,
        lastModified: Long
    ) = DocumentNode(Uri.parse("content://doc/$name"), name, name, "image/jpeg", lastModified = lastModified, size = lastModified)
}
