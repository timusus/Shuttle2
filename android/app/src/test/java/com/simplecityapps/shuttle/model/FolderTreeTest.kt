package com.simplecityapps.shuttle.model

import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import org.junit.Test

class FolderTreeTest {

    @Test
    fun `builds folders with recursive song counts`() {
        val tree = FolderTree.build(
            listOf(
                createSong(id = 1, path = "/storage/emulated/0/Music/Radiohead/a.mp3"),
                createSong(id = 2, path = "/storage/emulated/0/Music/Radiohead/b.mp3"),
                createSong(id = 3, path = "/storage/emulated/0/Music/c.mp3"),
                createSong(id = 4, path = "/storage/emulated/0/Podcasts/d.mp3"),
            )
        )

        val primary = tree.root.subfolders.single()
        primary.path shouldBe listOf("primary")
        primary.songCount shouldBe 4

        val music = primary.subfolders.first()
        music.name shouldBe "Music"
        music.songCount shouldBe 3
        music.songs.map { it.id } shouldBe listOf(3L)
        music.subfolders.single().songCount shouldBe 2
    }

    @Test
    fun `a MediaStore path and a SAF uri for the same folder share a node`() {
        val tree = FolderTree.build(
            listOf(
                createSong(id = 1, path = "/storage/emulated/0/Music/a.mp3"),
                createSong(id = 2, path = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fb.mp3"),
            )
        )

        tree.displayRoot.subfolders.single().songCount shouldBe 2
    }

    @Test
    fun `remote songs are excluded`() {
        val tree = FolderTree.build(
            listOf(
                createSong(id = 1, path = "/storage/emulated/0/Music/a.mp3"),
                createSong(id = 2, path = "https://jellyfin.example/Audio/2/stream", mediaProvider = MediaProviderType.Jellyfin),
            )
        )

        tree.root.songCount shouldBe 1
    }

    @Test
    fun `a library with only remote songs is empty`() {
        val tree = FolderTree.build(
            listOf(createSong(path = "https://plex.example/1", mediaProvider = MediaProviderType.Plex))
        )

        tree.isEmpty shouldBe true
    }

    @Test
    fun `folders and songs are in natural name order, with other locations last`() {
        val tree = FolderTree.build(
            listOf(
                createSong(id = 1, path = "content://media/external/audio/media/1"),
                createSong(id = 2, path = "/storage/emulated/0/Music/Track 10.mp3"),
                createSong(id = 3, path = "/storage/emulated/0/Music/track 2.mp3"),
                createSong(id = 4, path = "/storage/1234-ABCD/Music/a.mp3"),
            )
        )

        tree.root.subfolders.map { it.name } shouldBe listOf("1234-ABCD", "primary", SongFolder.OTHER_VOLUME)
        tree.root.nearest(listOf("primary", "Music")).songs.map { it.id } shouldBe listOf(3L, 2L)
    }

    @Test
    fun `a single volume is skipped at the top level`() {
        val tree = FolderTree.build(listOf(createSong(path = "/storage/emulated/0/Music/a.mp3")))

        tree.displayRoot.path shouldBe listOf("primary")
    }

    @Test
    fun `several volumes are shown at the top level`() {
        val tree = FolderTree.build(
            listOf(
                createSong(id = 1, path = "/storage/emulated/0/Music/a.mp3"),
                createSong(id = 2, path = "/storage/1234-ABCD/Music/a.mp3"),
            )
        )

        tree.displayRoot shouldBe tree.root
    }

    @Test
    fun `nearest falls back to the deepest existing ancestor`() {
        val tree = FolderTree.build(listOf(createSong(path = "/storage/emulated/0/Music/Radiohead/a.mp3")))

        tree.root.nearest(listOf("primary", "Music", "Radiohead")).name shouldBe "Radiohead"
        tree.root.nearest(listOf("primary", "Music", "Gone", "Deeper")).path shouldBe listOf("primary", "Music")
        tree.root.nearest(listOf("missing")) shouldBe tree.root
    }
}
