package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.ui.actions.MediaActionResult.Message
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaActionHandlerTest {

    private val songRepository = FakeSongRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val queueManager = FakeQueueManager()
    private val playbackManager = FakePlaybackManager()
    private val albumRepository = FakeAlbumRepository()
    private val actions = TestMediaActions(
        songRepository = songRepository,
        playlistRepository = playlistRepository,
        queueManager = queueManager,
        playbackManager = playbackManager,
        albumRepository = albumRepository,
    )
    private val handler = actions.handler

    private val song = createSong(id = 1, name = "Airbag")
    private val other = createSong(id = 2, name = "Paranoid Android")
    private val songs = MediaSelection.Songs(listOf(song, other))

    @Test
    fun `play queues the songs from the position and plays`() = runTest {
        handler.handle(MediaAction.Play(songs, position = 1)) shouldBe MediaActionResult.None

        queueManager.lastSetQueue shouldBe listOf(song, other)
        queueManager.lastSetQueuePosition shouldBe 1
    }

    @Test
    fun `play reports a load failure`() = runTest {
        playbackManager.loadResult = Result.failure(Exception("codec error"))

        handler.handle(MediaAction.Play(songs)) shouldBe Message(MediaActionMessage.PlaybackFailed("codec error"))
    }

    @Test
    fun `an empty selection says there are no songs`() = runTest {
        val empty = MediaSelection.Songs(emptyList())

        handler.handle(MediaAction.Play(empty)) shouldBe Message(MediaActionMessage.NoSongs)
        handler.handle(MediaAction.Shuffle(empty)) shouldBe Message(MediaActionMessage.NoSongs)
        handler.handle(MediaAction.AddToQueue(empty)) shouldBe Message(MediaActionMessage.NoSongs)
    }

    @Test
    fun `play next and add to queue report the count`() = runTest {
        handler.handle(MediaAction.PlayNext(songs)) shouldBe Message(MediaActionMessage.AddedToQueue(2))
        handler.handle(MediaAction.AddToQueue(MediaSelection.Songs(song))) shouldBe Message(MediaActionMessage.AddedToQueue(1))

        playbackManager.playedNext shouldBe listOf(song, other)
        playbackManager.addedToQueue shouldBe listOf(song)
    }

    @Test
    fun `duplicates offer add anyway, which adds them`() = runTest {
        val playlist = createPlaylist(id = 1, name = "Favourites")
        playlistRepository.setSongsForPlaylist(playlist, listOf(song))

        val result = handler.handle(MediaAction.AddToPlaylist(songs, playlist))

        result shouldBe Message(
            MediaActionMessage.AlreadyInPlaylist("Favourites", 1),
            SnackbarAction(SnackbarAction.Label.AddAnyway, MediaAction.AddToPlaylist(songs, playlist, ignoreDuplicates = true)),
        )
        handler.handle((result as Message).action!!.action) shouldBe Message(MediaActionMessage.AddedToPlaylist("Favourites", 2))
        playlistRepository.addedToPlaylist shouldBe listOf(playlist to listOf(song, other))
    }

    @Test
    fun `create playlist names the new playlist`() = runTest {
        handler.handle(MediaAction.CreatePlaylist(songs, "Road trip")) shouldBe Message(MediaActionMessage.PlaylistCreated("Road trip"))
    }

    @Test
    fun `exclude runs straight away and its undo includes the same songs`() = runTest {
        val result = handler.handle(MediaAction.Exclude(songs))

        result shouldBe Message(
            MediaActionMessage.Excluded(2),
            SnackbarAction(SnackbarAction.Label.Undo, MediaAction.Include(MediaSelection.Songs(listOf(song, other)))),
        )
        handler.handle((result as Message).action!!.action) shouldBe MediaActionResult.None
        songRepository.excludedCalls shouldBe listOf(listOf(song, other) to true, listOf(song, other) to false)
    }

    @Test
    fun `remove from playlist runs straight away and its undo restores the old order`() = runTest {
        val playlist = createPlaylist(id = 7, name = "Road trip")
        val third = createSong(id = 3, name = "Lucky")
        val before = listOf(song, other, third).mapIndexed { index, it -> PlaylistSong(id = index.toLong(), sortOrder = index.toLong(), song = it) }
        val removed = listOf(before[1])

        val result = handler.handle(MediaAction.RemoveFromPlaylist(playlist, removed, before))

        result shouldBe Message(
            MediaActionMessage.RemovedFromPlaylist("Road trip", 1),
            SnackbarAction(SnackbarAction.Label.Undo, MediaAction.RestoreToPlaylist(playlist, removed, before)),
        )
        playlistRepository.removedFromPlaylist shouldBe listOf(playlist to removed)

        // The repository appends the re-added song, so the playlist reads back as song, third, other.
        playlistRepository.setSongsForPlaylist(playlist, listOf(song, third))
        handler.handle((result as Message).action!!.action) shouldBe MediaActionResult.None

        playlistRepository.addedToPlaylist shouldBe listOf(playlist to listOf(other))
        playlistRepository.reorderedSongs!!.map { it.song to it.sortOrder } shouldBe listOf(song to 0L, other to 1L, third to 2L)
    }

    @Test
    fun `delete asks first, then deletes what was confirmed`() = runTest {
        val result = handler.handle(MediaAction.Delete(MediaSelection.Songs(song)))

        result shouldBe MediaActionResult.ConfirmationRequired(
            MediaActionMessage.ConfirmDelete("Airbag", 1),
            MediaAction.Delete(MediaSelection.Songs(song), confirmed = true),
        )
        songRepository.removed shouldBe emptyList()

        handler.handle((result as MediaActionResult.ConfirmationRequired).confirmAction) shouldBe Message(MediaActionMessage.Deleted(1))
        songRepository.removed shouldBe listOf(song)
    }

    @Test
    fun `a failed delete says how many failed`() = runTest {
        actions.fileDeleter = SongFileDeleter { false }

        handler.handle(MediaAction.Delete(songs, confirmed = true)) shouldBe Message(MediaActionMessage.DeleteFailed(2))
    }

    @Test
    fun `go to album navigates, or says it isn't there`() = runTest {
        handler.handle(MediaAction.GoToAlbum(MediaSelection.Songs(song))) shouldBe Message(MediaActionMessage.NotFound)

        val album = createAlbum()
        albumRepository.setAlbums(listOf(album))
        handler.handle(MediaAction.GoToAlbum(MediaSelection.Songs(song))) shouldBe MediaActionResult.Navigate(NavigationTarget.Album(album))
    }

    @Test
    fun `edit tags opens the editor for the songs that support it`() = runTest {
        val remote = createSong(id = 3, mediaProvider = MediaProviderType.Jellyfin)

        handler.handle(MediaAction.EditTags(MediaSelection.Songs(listOf(song, remote)))) shouldBe
            MediaActionResult.Navigate(NavigationTarget.TagEditor(listOf(song)))
    }

    @Test
    fun `song info opens the song`() = runTest {
        handler.handle(MediaAction.SongInfo(MediaSelection.Songs(song))) shouldBe MediaActionResult.Navigate(NavigationTarget.SongInfo(song))
    }

    @Test
    fun `share hands back the share request`() = runTest {
        handler.handle(MediaAction.Share(songs)).shouldBeInstanceOf<MediaActionResult.Share>()
    }

    @Test
    fun `download and remove download report the count`() = runTest {
        val remote = MediaSelection.Songs(createSong(id = 3, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://3"))

        handler.handle(MediaAction.Download(remote)) shouldBe Message(MediaActionMessage.DownloadQueued(1))
        handler.handle(MediaAction.RemoveDownload(remote)) shouldBe Message(MediaActionMessage.DownloadRemoved(1))
        handler.handle(MediaAction.Download(MediaSelection.Songs(song))) shouldBe Message(MediaActionMessage.NoSongs)
    }

    @Test
    fun `a download without a URL fails`() = runTest {
        actions.mediaInfoProvider.unavailable += "jellyfin://3"
        val remote = MediaSelection.Songs(createSong(id = 3, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://3"))

        handler.handle(MediaAction.Download(remote)) shouldBe Message(MediaActionMessage.DownloadFailed(1))
    }
}
