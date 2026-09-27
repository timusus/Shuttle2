package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class DownloadSongsTest {

    private val actions = TestMediaActions()
    private val remote = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")
    private val local = createSong(id = 2, mediaProvider = MediaProviderType.Shuttle, path = "content://2")

    @Test
    fun `downloads the remote songs and skips local ones`() = runTest {
        val result = actions.downloadSongs(MediaSelection.Songs(listOf(remote, local)))

        result shouldBe DownloadSongs.Result(changed = listOf(remote), failed = emptyList())
        actions.songDownloader.downloaded shouldBe listOf(remote)
    }

    @Test
    fun `a song without a download URL fails`() = runTest {
        actions.songDownloader.unavailable += remote.path

        val result = actions.downloadSongs(MediaSelection.Songs(remote))

        result shouldBe DownloadSongs.Result(changed = emptyList(), failed = listOf(remote))
        actions.songDownloader.downloaded.shouldBeEmpty()
    }

    @Test
    fun `removing drops the remote songs' downloads`() = runTest {
        val result = actions.downloadSongs(MediaSelection.Songs(listOf(remote, local)), download = false)

        result shouldBe DownloadSongs.Result(changed = listOf(remote), failed = emptyList())
        actions.songDownloader.removed shouldBe listOf(remote)
    }

    @Test
    fun `a user whose entitlement refuses server downloads downloads nothing and needs Pro`() = runTest {
        actions.downloadAllowed = false

        val result = actions.downloadSongs(MediaSelection.Songs(listOf(remote, local)))

        result shouldBe DownloadSongs.Result(changed = emptyList(), failed = emptyList(), needsPro = true)
        actions.songDownloader.downloaded.shouldBeEmpty()
    }

    @Test
    fun `a refused user can still remove downloads and local-only selections never ask`() = runTest {
        actions.downloadAllowed = false

        actions.downloadSongs(MediaSelection.Songs(remote), download = false).changed shouldBe listOf(remote)
        actions.downloadSongs(MediaSelection.Songs(local)).needsPro shouldBe false
    }
}
