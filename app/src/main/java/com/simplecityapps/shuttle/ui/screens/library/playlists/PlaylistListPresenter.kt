package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStorePlaylistImporter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

interface PlaylistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Loading : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setPlaylists(playlists: List<Playlist>, smartPlaylists: List<SmartPlaylist>)
        fun onAddedToQueue(playlist: Playlist)
        fun setLoadingState(state: LoadingState)
        fun showLoadError(error: Error)
        fun setLoadingProgress(progress: Float)
        fun onPlaylistsImported()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()
        fun play(playlist: Playlist)
        fun addToQueue(playlist: Playlist)
        fun playNext(playlist: Playlist)
        fun delete(playlist: Playlist)
        fun importMediaStorePlaylists()
        fun clear(playlist: Playlist)
        fun rename(playlist: Playlist, name: String)
    }
}

class PlaylistListPresenter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackManager,
    private val playlistImporter: MediaStorePlaylistImporter,
    private val queueManager: QueueManager
) : PlaylistListContract.Presenter,
    BasePresenter<PlaylistListContract.View>() {

    override fun loadPlaylists() {
        view?.setLoadingState(PlaylistListContract.LoadingState.Loading)

        launch {
            combine(
                playlistRepository.getPlaylists(PlaylistQuery.All()),
                playlistRepository.getSmartPlaylists()
            ) { smartPlaylists, playlists ->
                Pair(smartPlaylists, playlists)
            }
                .flowOn(Dispatchers.IO)
                .collect { (playlists, smartPlaylists) ->
                    if (playlists.isEmpty() && smartPlaylists.isEmpty()) {
                        view?.setLoadingState(PlaylistListContract.LoadingState.Empty)
                    } else {
                        view?.setLoadingState(PlaylistListContract.LoadingState.None)
                        view?.setPlaylists(playlists, smartPlaylists)
                    }
                }
        }
    }

    override fun delete(playlist: Playlist) {
        launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    override fun play(playlist: Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
            if (queueManager.setQueue(songs.map { it.song })) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun addToQueue(playlist: Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs.map { it.song })
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(playlist: Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
            playbackManager.playNext(songs.map { it.song })
            view?.onAddedToQueue(playlist)
        }
    }

    override fun importMediaStorePlaylists() {
        launch {
            playlistImporter.importPlaylists()
            view?.onPlaylistsImported()
        }
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
}