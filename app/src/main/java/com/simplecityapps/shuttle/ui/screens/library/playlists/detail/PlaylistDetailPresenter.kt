package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.PlaylistSong
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface PlaylistDetailContract {

    interface View {
        fun setData(playlistSongs: List<PlaylistSong>, showDragHandle: Boolean)
        fun updateToolbarMenuSortOrder(sortOrder: PlaylistSongSortOrder)
        fun showLoadError(error: Error)
        fun onAddedToQueue(playlistSong: PlaylistSong)
        fun onAddedToQueue(playlist: Playlist)
        fun setPlaylist(playlist: Playlist)
        fun showDeleteError(error: Error)
        fun showTagEditor(playlistSongs: List<PlaylistSong>)
        fun dismiss()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun onSongClicked(playlistSong: PlaylistSong, index: Int)
        fun shuffle()
        fun addToQueue(playlistSong: PlaylistSong)
        fun playNext(playlistSong: PlaylistSong)
        fun exclude(playlistSong: PlaylistSong)
        fun editTags(playlistSong: PlaylistSong)
        fun remove(playlistSong: PlaylistSong)
        fun delete(playlistSong: PlaylistSong)
        fun addToQueue(playlist: Playlist)
        fun delete(playlist: Playlist)
        fun clear(playlist: Playlist)
        fun rename(playlist: Playlist, name: String)
        fun setSortOrder(sortOrder: PlaylistSongSortOrder)
        fun updateToolbarMenu()
        fun movePlaylistItem(from: Int, to: Int)
    }
}

class PlaylistDetailPresenter @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted playlist: Playlist
) : BasePresenter<PlaylistDetailContract.View>(),
    PlaylistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(playlist: Playlist): PlaylistDetailPresenter
    }

    private val playlist = playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(playlist.id))
        .map { playlists ->
            playlists.firstOrNull()
        }
        .filterNotNull()
        .stateIn(
            scope = this,
            started = SharingStarted.WhileSubscribed(),
            initialValue = playlist
        )

    private val playlistSongs: StateFlow<List<PlaylistSong>?> = this.playlist
        .flatMapLatest { playlist ->
            playlistRepository.getSongsForPlaylist(playlist)
        }
        .stateIn(
            scope = this,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    override fun bindView(view: PlaylistDetailContract.View) {
        super.bindView(view)

        playlist.onEach { playlist ->
            this@PlaylistDetailPresenter.view?.setPlaylist(playlist)
            updateToolbarMenu()
        }.launchIn(this)

        playlistSongs
            .onStart {
                Timber.i("playlistSongs.onStart()")
            }
            .filterNotNull()
            .onEach { playlistSongs ->
                this@PlaylistDetailPresenter.view?.setData(
                    playlistSongs = playlistSongs,
                    showDragHandle = playlist.value.sortOrder == PlaylistSongSortOrder.Position
                )
            }.launchIn(this)
    }

    override fun onSongClicked(playlistSong: PlaylistSong, index: Int) {
        launch {
            if (queueManager.setQueue(songs = playlistSongs.value.orEmpty().map { it.song }, position = index)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun shuffle() {
        if (playlistSongs.value.orEmpty().isNotEmpty()) {
            launch {
                playbackManager.shuffle(playlistSongs.value.orEmpty().map { it.song }) { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        } else {
            Timber.i("Shuffle failed: Songs list empty")
        }
    }

    override fun addToQueue(playlistSong: PlaylistSong) {
        launch {
            playbackManager.addToQueue(listOf(playlistSong.song))
            view?.onAddedToQueue(playlistSong)
        }
    }

    override fun addToQueue(playlist: Playlist) {
        launch {
            playbackManager.addToQueue(playlistSongs.value.orEmpty().map { it.song })
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(playlistSong: PlaylistSong) {
        launch {
            playbackManager.playNext(listOf(playlistSong.song))
            view?.onAddedToQueue(playlistSong)
        }
    }

    override fun exclude(playlistSong: PlaylistSong) {
        launch {
            songRepository.setExcluded(listOf(playlistSong.song), true)
            queueManager.remove(queueManager.getQueue().filter { it.song.id == playlistSong.song.id })
        }
    }

    override fun editTags(playlistSong: PlaylistSong) {
        view?.showTagEditor(listOf(playlistSong))
    }

    override fun remove(playlistSong: PlaylistSong) {
        launch {
            playlistRepository.removeFromPlaylist(playlist.value, listOf(playlistSong))
        }
    }

    override fun delete(playlistSong: PlaylistSong) {
        val uri = playlistSong.song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(playlistSong.song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.id == playlistSong.song.id })
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

    override fun rename(playlist: Playlist, name: String) {
        launch {
            playlistRepository.renamePlaylist(playlist, name)
        }
    }

    override fun setSortOrder(sortOrder: PlaylistSongSortOrder) {
        if (playlist.value.sortOrder != sortOrder) {
            launch {
                withContext(Dispatchers.IO) {
                    playlistRepository.updatePlaylistSortOder(playlist.value, sortOrder)
                }
                view?.updateToolbarMenuSortOrder(sortOrder)
            }
        }
    }

    override fun updateToolbarMenu() {
        view?.updateToolbarMenuSortOrder(playlist.value.sortOrder)
    }

    override fun movePlaylistItem(from: Int, to: Int) {
        launch {
            var newSongs = playlistSongs.value.orEmpty().toMutableList()
            newSongs.add(to, newSongs.removeAt(from))
            newSongs = newSongs.mapIndexed { index, playlistSong -> PlaylistSong(playlistSong.id, index.toLong(), playlistSong.song) }.toMutableList()
            playlistRepository.updatePlaylistSongsSortOder(playlist.value, newSongs)
        }
    }
}