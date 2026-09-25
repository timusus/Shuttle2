package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.content.Context
import android.net.Uri
import com.simplecityapps.mediaprovider.PlaylistExporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface PlaylistDetailContract {
    interface View {
        fun setData(
            playlistSongs: List<PlaylistSong>,
            showDragHandle: Boolean
        )

        fun updateToolbarMenuSortOrder(
            sortOrder: PlaylistSongSortOrder,
            sortDescending: Boolean
        )

        fun showLoadError(error: Error)

        fun onAddedToQueue(playlistSong: PlaylistSong)

        fun onAddedToQueue(playlist: Playlist)

        fun setPlaylist(playlist: Playlist)

        fun showDeleteError(error: Error)

        fun showTagEditor(playlistSongs: List<PlaylistSong>)

        fun dismiss()

        fun showExportSuccess()

        fun showExportError(error: String)

        fun showExportLocationPicker()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun onSongClicked(
            playlistSong: PlaylistSong,
            index: Int
        )

        fun shuffle()

        fun addToQueue(playlistSong: PlaylistSong)

        fun addToQueue(playlistSongs: List<PlaylistSong>)

        fun playNext(playlistSong: PlaylistSong)

        fun exclude(playlistSong: PlaylistSong)

        fun editTags(playlistSong: PlaylistSong)

        fun editTags(playlistSongs: List<PlaylistSong>)

        fun remove(playlistSong: PlaylistSong)

        fun delete(playlistSong: PlaylistSong)

        fun addToQueue(playlist: Playlist)

        fun delete(playlist: Playlist)

        fun clear(playlist: Playlist)

        fun rename(
            playlist: Playlist,
            name: String
        )

        fun setSortOrder(sortOrder: PlaylistSongSortOrder)

        fun setSortDescending(sortDescending: Boolean)

        fun updateToolbarMenu()

        fun movePlaylistItem(
            from: Int,
            to: Int
        )

        fun exportPlaylist()

        fun exportPlaylistToUri(uri: Uri)
    }
}

class PlaylistDetailPresenter
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    @Assisted playlist: Playlist
) : BasePresenter<PlaylistDetailContract.View>(),
    PlaylistDetailContract.Presenter {
    @AssistedFactory
    interface Factory {
        fun create(playlist: Playlist): PlaylistDetailPresenter
    }

    private val playlistExporter = PlaylistExporter(context)

    private val playlist =
        playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(playlist.id))
            .map { playlists ->
                playlists.firstOrNull()
            }
            .filterNotNull()
            .stateIn(
                scope = this,
                started = SharingStarted.Lazily,
                initialValue = playlist
            )

    private val playlistSongs: StateFlow<List<PlaylistSong>?> =
        this.playlist
            .flatMapLatest { playlist ->
                playlistRepository.getSongsForPlaylist(playlist)
            }
            .stateIn(
                scope = this,
                started = SharingStarted.Lazily,
                initialValue = null
            )

    override fun bindView(view: PlaylistDetailContract.View) {
        super.bindView(view)

        playlist.onEach { playlist ->
            this@PlaylistDetailPresenter.view?.setPlaylist(playlist)
            updateToolbarMenu()
        }.launchIn(this)

        playlistSongs
            .filterNotNull()
            .onEach { playlistSongs ->
                this@PlaylistDetailPresenter.view?.setData(
                    playlistSongs = playlistSongs,
                    showDragHandle = playlist.value.sortOrder == PlaylistSongSortOrder.Position && !playlist.value.sortDescending
                )
            }.launchIn(this)
    }

    override fun onSongClicked(
        playlistSong: PlaylistSong,
        index: Int
    ) {
        launch {
            val songs = playlistSongs.value.orEmpty().map { it.song }
            if (songs.isEmpty()) return@launch
            val result = playSongs(songs, index.coerceIn(0, songs.lastIndex))
            if (result is PlaySongs.Result.Failure && result.message != null) view?.showLoadError(Error(result.message))
        }
    }

    override fun shuffle() {
        if (playlistSongs.value.orEmpty().isNotEmpty()) {
            launch {
                val result = shuffleSongs(playlistSongs.value.orEmpty().map { it.song })
                if (result is ShuffleSongs.Result.Failure) view?.showLoadError(Error(result.message))
            }
        } else {
            Timber.i("Shuffle failed: Songs list empty")
        }
    }

    override fun addToQueue(playlistSong: PlaylistSong) {
        launch {
            enqueueSongs(MediaSelection.Songs(playlistSong.song), EnqueueSongs.Position.End)
            view?.onAddedToQueue(playlistSong)
        }
    }

    override fun addToQueue(playlistSongs: List<PlaylistSong>) {
        launch {
            enqueueSongs(MediaSelection.Songs(playlistSongs.map { it.song }), EnqueueSongs.Position.End)
        }
    }

    override fun addToQueue(playlist: Playlist) {
        launch {
            enqueueSongs(MediaSelection.Songs(playlistSongs.value.orEmpty().map { it.song }), EnqueueSongs.Position.End)
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(playlistSong: PlaylistSong) {
        launch {
            enqueueSongs(MediaSelection.Songs(playlistSong.song), EnqueueSongs.Position.Next)
            view?.onAddedToQueue(playlistSong)
        }
    }

    override fun exclude(playlistSong: PlaylistSong) {
        launch { excludeSongs(MediaSelection.Songs(playlistSong.song)) }
    }

    override fun editTags(playlistSong: PlaylistSong) {
        view?.showTagEditor(listOf(playlistSong))
    }

    override fun editTags(playlistSongs: List<PlaylistSong>) {
        view?.showTagEditor(playlistSongs)
    }

    override fun remove(playlistSong: PlaylistSong) {
        launch {
            playlistRepository.removeFromPlaylist(playlist.value, listOf(playlistSong))
        }
    }

    override fun delete(playlistSong: PlaylistSong) {
        launch {
            if (deleteSongs(MediaSelection.Songs(playlistSong.song)).failed.isNotEmpty()) {
                view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
            }
        }
    }

    override fun delete(playlist: Playlist) {
        launch {
            playlistRepository.deletePlaylist(playlist)
        }
        view?.dismiss()
    }

    override fun clear(playlist: Playlist) {
        launch {
            playlistRepository.clearPlaylist(playlist)
        }
    }

    override fun rename(
        playlist: Playlist,
        name: String
    ) {
        launch {
            playlistRepository.renamePlaylist(playlist, name)
        }
    }

    override fun setSortOrder(sortOrder: PlaylistSongSortOrder) {
        if (playlist.value.sortOrder != sortOrder) {
            launch {
                withContext(Dispatchers.IO) {
                    playlistRepository.updatePlaylistSortOder(playlist.value, sortOrder, playlist.value.sortDescending)
                }
                view?.updateToolbarMenuSortOrder(sortOrder, playlist.value.sortDescending)
            }
        }
    }

    override fun setSortDescending(sortDescending: Boolean) {
        if (playlist.value.sortDescending != sortDescending) {
            launch {
                withContext(Dispatchers.IO) {
                    playlistRepository.updatePlaylistSortOder(playlist.value, playlist.value.sortOrder, sortDescending)
                }
                view?.updateToolbarMenuSortOrder(playlist.value.sortOrder, sortDescending)
            }
        }
    }

    override fun updateToolbarMenu() {
        view?.updateToolbarMenuSortOrder(playlist.value.sortOrder, playlist.value.sortDescending)
    }

    override fun movePlaylistItem(
        from: Int,
        to: Int
    ) {
        launch {
            var newSongs = playlistSongs.value.orEmpty().toMutableList()
            newSongs.add(to, newSongs.removeAt(from))
            newSongs = newSongs.mapIndexed { index, playlistSong -> PlaylistSong(playlistSong.id, index.toLong(), playlistSong.song) }.toMutableList()
            playlistRepository.updatePlaylistSongsSortOder(playlist.value, newSongs)
        }
    }

    override fun exportPlaylist() {
        if (playlistSongs.value.orEmpty().isEmpty()) {
            view?.showExportError(context.getString(R.string.playlist_export_empty))
            return
        }
        view?.showExportLocationPicker()
    }

    override fun exportPlaylistToUri(uri: Uri) {
        launch {
            when (val result = playlistExporter.exportToUri(playlist.value.name, playlistSongs.value.orEmpty().map { it.song }, uri)) {
                is PlaylistExporter.ExportResult.Success -> view?.showExportSuccess()
                is PlaylistExporter.ExportResult.Failure -> view?.showExportError(result.error)
            }
        }
    }
}
