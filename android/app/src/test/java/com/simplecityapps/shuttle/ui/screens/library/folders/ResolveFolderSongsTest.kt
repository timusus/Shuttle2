package com.simplecityapps.shuttle.ui.screens.library.folders

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveFolderSongsTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val resolveFolderSongs = ResolveFolderSongs(songRepository)

    private val airbag = createSong(id = 1, path = "/storage/emulated/0/Music/Radiohead/01 Airbag.mp3")
    private val paranoid = createSong(id = 2, path = "/storage/emulated/0/Music/Radiohead/02 Paranoid Android.mp3")
    private val loose = createSong(id = 3, path = "/storage/emulated/0/Music/loose.mp3")
    private val podcast = createSong(id = 4, path = "/storage/emulated/0/Podcasts/episode.mp3")

    @Test
    fun `resolves songs in the folder and its subfolders, in browsing order`() = runTest {
        songRepository.setSongs(listOf(loose, podcast, paranoid, airbag))

        resolveFolderSongs(listOf(listOf("primary", "Music"))) shouldBe listOf(airbag, paranoid, loose)
    }

    @Test
    fun `resolves several folders in order without duplicates`() = runTest {
        songRepository.setSongs(listOf(loose, podcast, paranoid, airbag))

        resolveFolderSongs(
            listOf(
                listOf("primary", "Podcasts"),
                listOf("primary", "Music", "Radiohead"),
                listOf("primary", "Music"),
            )
        ) shouldBe listOf(podcast, airbag, paranoid, loose)
    }

    @Test
    fun `ignores remote songs`() = runTest {
        val remote = createSong(id = 5, path = "/storage/emulated/0/Music/remote.mp3", mediaProvider = MediaProviderType.Jellyfin)
        songRepository.setSongs(listOf(loose, remote))

        resolveFolderSongs(listOf(listOf("primary", "Music"))) shouldBe listOf(loose)
    }

    @Test
    fun `an unknown folder resolves to no songs`() = runTest {
        songRepository.setSongs(listOf(loose))

        resolveFolderSongs(listOf(listOf("primary", "Missing"))) shouldBe emptyList()
    }
}
