package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createPlatformFeatures
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeCopyAvailabilitySource
import com.simplecityapps.fakes.FakeCopyPreferencesStore
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongDownloader
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.fakeObserveCopyContext
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.MediaActionType.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.MediaActionType.AddToQueue
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Delete
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Download
import com.simplecityapps.shuttle.ui.actions.MediaActionType.EditTags
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Exclude
import com.simplecityapps.shuttle.ui.actions.MediaActionType.GoToAlbum
import com.simplecityapps.shuttle.ui.actions.MediaActionType.GoToArtist
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Play
import com.simplecityapps.shuttle.ui.actions.MediaActionType.PlayAfterWork
import com.simplecityapps.shuttle.ui.actions.MediaActionType.PlayFrom
import com.simplecityapps.shuttle.ui.actions.MediaActionType.PlayNext
import com.simplecityapps.shuttle.ui.actions.MediaActionType.RemoveDownload
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Share
import com.simplecityapps.shuttle.ui.actions.MediaActionType.Shuffle
import com.simplecityapps.shuttle.ui.actions.MediaActionType.SongInfo
import com.simplecityapps.shuttle.ui.actions.MediaActionType.StartRadio
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AvailableMediaActionsTest {

    private val downloads = FakeSongDownloader()
    private val queueOperations = FakeQueueOperations()
    private val settingsStore = SettingsStore(InMemoryKeyValueStore())
    private val copyStore = FakeCopyPreferencesStore()
    private val copyAvailability = FakeCopyAvailabilitySource()
    private val copyContext = fakeObserveCopyContext(copyStore, copyAvailability)
    private val availableActions = AvailableMediaActions(TestMediaActions().resolveSongs, downloads, createPlatformFeatures(), queueOperations, ObserveSetting(settingsStore), copyContext)

    private val remote = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")

    private suspend fun actionsFor(selection: MediaSelection) = availableActions(selection).first()

    @Test
    fun `a single local song offers everything but downloads`() = runTest {
        actionsFor(MediaSelection.Songs(createSong())) shouldBe
            listOf(Play, Shuffle, PlayNext, AddToQueue, AddToPlaylist, StartRadio, GoToAlbum, GoToArtist, EditTags, SongInfo, Share, Exclude, Delete)
    }

    private fun movement(id: Long) = createSong(id = id, name = "Movement $id", track = id.toInt()).copy(work = "Symphony No. 5", composer = "Beethoven", movementNumber = id.toInt())

    private fun playing(position: Int, vararg songs: Song) {
        val items = songs.mapIndexed { index, song -> QueueItem(uid = index.toLong(), song = song, isCurrent = index == position) }
        queueOperations.queueStateFlow.value = QueueState(items = items, currentItem = items[position], currentPosition = position)
    }

    @Test
    fun `play after this work follows play next while a work is playing`() = runTest {
        playing(1, movement(1), movement(2), movement(3), createSong(id = 9))

        actionsFor(MediaSelection.Songs(createSong(id = 20))).take(5) shouldBe listOf(Play, Shuffle, PlayNext, PlayAfterWork, AddToQueue)
    }

    @Test
    fun `play after this work is not offered for a lone track`() = runTest {
        playing(0, createSong(id = 8), createSong(id = 9))

        actionsFor(MediaSelection.Songs(createSong(id = 20))) shouldNotContain PlayAfterWork
    }

    @Test
    fun `play after this work is not offered with classical display off`() = runTest {
        SaveSetting(settingsStore)(AppearanceSettings.ClassicalDisplay, false)
        playing(1, movement(1), movement(2), movement(3))

        actionsFor(MediaSelection.Songs(createSong(id = 20))) shouldNotContain PlayAfterWork
    }

    @Test
    fun `play after this work is not offered for the queue itself`() = runTest {
        playing(1, movement(1), movement(2), movement(3))

        actionsFor(MediaSelection.Queue) shouldNotContain PlayAfterWork
    }

    @Test
    fun `radio starts from one song or album but not from several or a genre`() = runTest {
        actionsFor(MediaSelection.Songs(createSong())) shouldContain StartRadio
        actionsFor(MediaSelection.Albums(createAlbum())) shouldContain StartRadio
        actionsFor(MediaSelection.Songs(listOf(createSong(id = 1), createSong(id = 2)))) shouldNotContain StartRadio
        actionsFor(MediaSelection.Genres(createGenre())) shouldNotContain StartRadio
    }

    @Test
    fun `the queue only offers saving it to a playlist`() = runTest {
        actionsFor(MediaSelection.Queue) shouldBe listOf(AddToPlaylist)
    }

    @Test
    fun `a single album goes to its artist but not an album`() = runTest {
        actionsFor(MediaSelection.Albums(createAlbum())) shouldBe
            listOf(Play, Shuffle, PlayNext, AddToQueue, AddToPlaylist, StartRadio, GoToArtist, EditTags, Share, Exclude)
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
        val withoutDownloads = AvailableMediaActions(TestMediaActions().resolveSongs, downloads, createPlatformFeatures(offlineDownloads = false), queueOperations, ObserveSetting(settingsStore), copyContext)

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

    private fun mergedSong(vararg providers: MediaProviderType): Song {
        val copies = providers.mapIndexed { index, provider -> createSong(id = index + 1L, mediaProvider = provider, path = "$provider://$index") }
        return copies.first().copy(copies = copies)
    }

    @Test
    fun `play from follows play for a song with copies in two sources`() = runTest {
        val song = mergedSong(MediaProviderType.Shuttle, MediaProviderType.Emby)

        actionsFor(MediaSelection.Songs(song)).take(3) shouldBe listOf(Play, PlayFrom, Shuffle)
    }

    @Test
    fun `play from is not offered with merging off or one source or several songs`() = runTest {
        val song = mergedSong(MediaProviderType.Shuttle, MediaProviderType.Emby)
        actionsFor(MediaSelection.Songs(mergedSong(MediaProviderType.Emby, MediaProviderType.Emby))) shouldNotContain PlayFrom
        actionsFor(MediaSelection.Songs(listOf(song, song))) shouldNotContain PlayFrom

        copyStore.mergeCopies.value = false

        actionsFor(MediaSelection.Songs(song)) shouldNotContain PlayFrom
    }

    @Test
    fun `play from is not offered while fewer than two copies can play`() = runTest {
        val song = mergedSong(MediaProviderType.Shuttle, MediaProviderType.Emby)

        copyAvailability.availability.value = SongCopyAvailability(unreachableProviders = setOf(MediaProviderType.Emby))
        actionsFor(MediaSelection.Songs(song)) shouldNotContain PlayFrom

        copyAvailability.availability.value = SongCopyAvailability(missingSongIds = setOf(1L))
        actionsFor(MediaSelection.Songs(song)) shouldNotContain PlayFrom

        copyAvailability.availability.value = SongCopyAvailability()
        actionsFor(MediaSelection.Songs(song)) shouldContain PlayFrom
    }
}
