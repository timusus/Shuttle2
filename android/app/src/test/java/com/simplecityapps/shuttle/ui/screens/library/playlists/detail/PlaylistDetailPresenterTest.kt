package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDetailPresenterTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaybackManager = FakePlaybackManager()
    private val fakeQueueManager = FakeQueueManager()

    private var lastPlaylistSongs: List<PlaylistSong> = emptyList()
    private var lastSortOrder: PlaylistSongSortOrder? = null
    private var lastSortDescending: Boolean? = null
    private var lastExportError: String? = null
    private var exportLocationPickerShown = false

    private val view =
        object : PlaylistDetailContract.View {
            override fun setData(playlistSongs: List<PlaylistSong>, showDragHandle: Boolean) {
                lastPlaylistSongs = playlistSongs
            }

            override fun updateToolbarMenuSortOrder(sortOrder: PlaylistSongSortOrder, sortDescending: Boolean) {
                lastSortOrder = sortOrder
                lastSortDescending = sortDescending
            }

            override fun showLoadError(error: Error) {}

            override fun onAddedToQueue(playlistSong: PlaylistSong) {}

            override fun onAddedToQueue(playlist: com.simplecityapps.shuttle.model.Playlist) {}

            override fun setPlaylist(playlist: com.simplecityapps.shuttle.model.Playlist) {}

            override fun showDeleteError(error: Error) {}

            override fun showTagEditor(playlistSongs: List<PlaylistSong>) {}

            override fun dismiss() {}

            override fun showExportSuccess() {}

            override fun showExportError(error: String) {
                lastExportError = error
            }

            override fun showExportLocationPicker() {
                exportLocationPickerShown = true
            }
        }

    private fun createPresenter(playlist: com.simplecityapps.shuttle.model.Playlist) = PlaylistDetailPresenter(
        context = ApplicationProvider.getApplicationContext(),
        playlistRepository = fakePlaylistRepository,
        songRepository = fakeSongRepository,
        playbackManager = fakePlaybackManager,
        queueManager = fakeQueueManager,
        playlist = playlist
    )

    @Test
    fun `toggling sortDescending persists the direction and re-sorts the displayed songs`() = runTest {
        val playlist = createPlaylist(id = 1L, sortOrder = PlaylistSongSortOrder.Position, sortDescending = false)
        fakePlaylistRepository.setPlaylists(listOf(playlist))
        fakePlaylistRepository.setSongsForPlaylist(
            playlist,
            listOf(
                createSong(name = "First"),
                createSong(name = "Second"),
                createSong(name = "Third")
            )
        )

        val presenter = createPresenter(playlist)
        presenter.bindView(view)

        lastPlaylistSongs.map { it.song.name } shouldBe listOf("First", "Second", "Third")

        val childrenBeforeToggle = presenter.coroutineContext[Job]!!.children.toSet()
        presenter.setSortDescending(true)
        (presenter.coroutineContext[Job]!!.children.toSet() - childrenBeforeToggle).forEach { it.join() }

        lastSortDescending shouldBe true
        lastPlaylistSongs.map { it.song.name } shouldBe listOf("Third", "Second", "First")

        presenter.unbindView()
    }

    @Test
    fun `updateToolbarMenu reports the playlist's saved sort direction on open`() = runTest {
        val playlist = createPlaylist(id = 1L, sortOrder = PlaylistSongSortOrder.SongName, sortDescending = true)
        fakePlaylistRepository.setPlaylists(listOf(playlist))
        fakePlaylistRepository.setSongsForPlaylist(playlist, emptyList())

        val presenter = createPresenter(playlist)
        presenter.bindView(view)
        presenter.updateToolbarMenu()

        lastSortOrder shouldBe PlaylistSongSortOrder.SongName
        lastSortDescending shouldBe true

        presenter.unbindView()
    }

    @Test
    fun `exporting an empty playlist shows an error instead of a location picker`() = runTest {
        val playlist = createPlaylist(id = 1L)
        fakePlaylistRepository.setPlaylists(listOf(playlist))
        fakePlaylistRepository.setSongsForPlaylist(playlist, emptyList())

        val presenter = createPresenter(playlist)
        presenter.bindView(view)

        presenter.exportPlaylist()

        exportLocationPickerShown shouldBe false
        lastExportError shouldBe ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.playlist_export_empty)

        presenter.unbindView()
    }

    @Test
    fun `exporting a non-empty playlist shows the location picker`() = runTest {
        val playlist = createPlaylist(id = 1L)
        fakePlaylistRepository.setPlaylists(listOf(playlist))
        fakePlaylistRepository.setSongsForPlaylist(playlist, listOf(createSong(name = "First")))

        val presenter = createPresenter(playlist)
        presenter.bindView(view)

        presenter.exportPlaylist()

        exportLocationPickerShown shouldBe true
        lastExportError shouldBe null

        presenter.unbindView()
    }
}
