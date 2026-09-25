package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShareSongsTest {

    private val shareSongs = TestMediaActions().shareSongs

    private fun Song.expectedText() = listOfNotNull(name, friendlyArtistName).joinToString(" – ")

    @Test
    fun `nothing to share for an empty selection`() = runTest {
        shareSongs(MediaSelection.Songs(emptyList())).shouldBeNull()
    }

    @Test
    fun `local files are attached, remote songs are text only`() = runTest {
        val local = createSong(id = 1, name = "Chlorophyll Loop", path = "content://media/1")
        val remote = createSong(id = 2, name = "Sodium Light", mediaProvider = MediaProviderType.Jellyfin, path = "content://remote/2")

        val request = shareSongs(MediaSelection.Songs(listOf(local, remote)))!!

        request.text shouldBe "${local.expectedText()}\n${remote.expectedText()}"
        request.streams shouldBe listOf(local.path)
        request.mimeType shouldBe local.mimeType
    }

    @Test
    fun `songs without a content path are shared as text`() = runTest {
        val song = createSong(id = 1, path = "/storage/emulated/0/Music/a.mp3")

        val request = shareSongs(MediaSelection.Songs(song))!!

        request.streams.shouldBeEmpty()
        request.mimeType shouldBe "text/plain"
    }
}
