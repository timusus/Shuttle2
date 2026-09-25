package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadSongsTest {

    private val actions = TestMediaActions()
    private val remote = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")
    private val local = createSong(id = 2, mediaProvider = MediaProviderType.Shuttle, path = "content://2")

    @Test
    fun `downloads the remote songs and skips local ones`() = runTest {
        val result = actions.downloadSongs(MediaSelection.Songs(listOf(remote, local)))

        result shouldBe DownloadSongs.Result(changed = listOf(remote), failed = emptyList())
        actions.songDownloadManager.downloaded.map { it.first } shouldBe listOf(remote)
        actions.songDownloadManager.downloaded.single().second.toString() shouldBe "https://example.com/download/1"
    }

    @Test
    fun `a song without a download URL fails`() = runTest {
        actions.mediaInfoProvider.unavailable += remote.path

        val result = actions.downloadSongs(MediaSelection.Songs(remote))

        result shouldBe DownloadSongs.Result(changed = emptyList(), failed = listOf(remote))
        actions.songDownloadManager.downloaded.shouldBeEmpty()
    }

    @Test
    fun `removing drops the remote songs' downloads`() = runTest {
        val result = actions.downloadSongs(MediaSelection.Songs(listOf(remote, local)), download = false)

        result shouldBe DownloadSongs.Result(changed = listOf(remote), failed = emptyList())
        actions.songDownloadManager.removed shouldBe listOf(remote)
    }
}
