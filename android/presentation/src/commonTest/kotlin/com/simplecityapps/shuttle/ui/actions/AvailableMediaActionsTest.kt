package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createPlatformFeatures
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongDownloader
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.MediaActionType.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.MediaActionType.AddToQueue
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Delete
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Download
import com.simplecityapps.shuttle.ui.actions.MediaActionType.EditTags
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Exclude
import com.simplecityapps.shuttle.ui.actions.MediaActionType.GoToAlbum
import com.simplecityapps.shuttle.ui.actions.MediaActionType.GoToArtist
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Play
import com.simplecityapps.shuttle.ui.actions.MediaActionType.PlayNext
import com.simplecityapps.shuttle.ui.actions.MediaActionType.RemoveDownload
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Share
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Shuffle
import com.simplecityapps.shuttle.ui.actions.MediaActionType.SongInfo
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AvailableMediaActionsTest {

    private val downloads = FakeSongDownloader()
    private val availableActions = AvailableMediaActions(TestMediaActions().resolveSongs, downloads, createPlatformFeatures())

    private val remote = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")

    private suspend fun actionsFor(selection: MediaSelection) = availableActions(selection).first()

    @Test
    fun `a single local song offers everything but downloads`() = runTest {
        actionsFor(MediaSelection.Songs(createSong())) shouldBe
            listOf(Play, Shuffle, PlayNext, AddToQueue, AddToPlaylist, GoToAlbum, GoToArtist, EditTags, SongInfo, Share, Exclude, Delete)
    }

    @Test
    fun `the queue only offers saving it to a playlist`() = runTest {
        actionsFor(MediaSelection.Queue) shouldBe listOf(AddToPlaylist)
    }

    @Test
    fun `a single album goes to its artist but not an album`() = runTest {
        actionsFor(MediaSelection.Albums(createAlbum())) shouldBe
            listOf(Play, Shuffle, PlayNext, AddToQueue, AddToPlaylist, GoToArtist, EditTags, Share, Exclude)
    }

    @Test
    fun `a MediaStore song can be deleted but a server song can't`() = runTest {
        val mediaStore = createSong(id = 2, mediaProvider = MediaProviderType.MediaStore).copy(externalId = "7")

        actionsFor(MediaSelection.Songs(mediaStore)) shouldContain Delete
        actionsFor(MediaSelection.Songs(listOf(mediaStore, remote))) shouldNotContain Delete
        actionsFor(MediaSelection.Songs(remote)) shouldNotContain Delete
    }

    @Test
    fun `genres can't be shared or deleted`() = runTest {
        val actions = actionsFor(MediaSelection.Genres(createGenre()))

        actions shouldNotContain Share
        actions shouldNotContain Delete
        actions shouldContainAll listOf(Exclude, EditTags)
    }

    @Test
    fun `a platform without offline downloads offers neither download action`() = runTest {
        downloads.heldPaths.value = setOf(remote.path)
        val withoutDownloads = AvailableMediaActions(TestMediaActions().resolveSongs, downloads, createPlatformFeatures(offlineDownloads = false))

        val actions = withoutDownloads(MediaSelection.Songs(listOf(remote, createSong(id = 2, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://2")))).first()

        actions shouldNotContain Download
        actions shouldNotContain RemoveDownload
        actions shouldContainAll listOf(Play, Exclude)
    }

    @Test
    fun `a remote song offers download until it is downloaded then removal`() = runTest {
        val selection = MediaSelection.Songs(remote)

        val before = actionsFor(selection)
        before shouldContainAll listOf(Download)
        before shouldNotContain RemoveDownload
        before shouldNotContain EditTags
        before shouldNotContain Delete

        downloads.heldPaths.value = setOf(remote.path)
        val after = actionsFor(selection)
        after shouldContainAll listOf(RemoveDownload)
        after shouldNotContain Download
    }

    @Test
    fun `a failed download can be retried`() = runTest {
        downloads.heldPaths.value = emptySet()

        actionsFor(MediaSelection.Songs(remote)) shouldContainAll listOf(Download)
    }
}
