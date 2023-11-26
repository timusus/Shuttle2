package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

interface PlaylistListContract {
    sealed class LoadingState {
        object Scanning : LoadingState()

        object Loading : LoadingState()

        object Empty : LoadingState()

        object None : LoadingState()
    }

    interface View {
        fun setPlaylists(
            playlists: List<com.simplecityapps.shuttle.model.Playlist>,
            smartPlaylists: List<com.simplecityapps.shuttle.model.SmartPlaylist>
        )

        fun onAddedToQueue(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun setLoadingState(state: LoadingState)

        fun showLoadError(error: Error)

        fun setLoadingProgress(progress: Float)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()

        fun play(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun addToQueue(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun playNext(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun delete(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun clear(playlist: com.simplecityapps.shuttle.model.Playlist)

        fun rename(
            playlist: com.simplecityapps.shuttle.model.Playlist,
            name: String
        )
    }
}

class PlaylistListPresenter
@Inject
constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager
) : PlaylistListContract.Presenter,
    BasePresenter<PlaylistListContract.View>() {
    override fun loadPlaylists() {
        view?.setLoadingState(PlaylistListContract.LoadingState.Loading)

        launch {
            combine(
                playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
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

    override fun delete(playlist: com.simplecityapps.shuttle.model.Playlist) {
        launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    override fun play(playlist: com.simplecityapps.shuttle.model.Playlist) {
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

    override fun addToQueue(playlist: com.simplecityapps.shuttle.model.Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs.map { it.song })
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(playlist: com.simplecityapps.shuttle.model.Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
            playbackManager.playNext(songs.map { it.song })
            view?.onAddedToQueue(playlist)
        }
    }

    override fun clear(playlist: com.simplecityapps.shuttle.model.Playlist) {
        launch {
            playlistRepository.clearPlaylist(playlist)
        }
    }

    override fun rename(
        playlist: com.simplecityapps.shuttle.model.Playlist,
        name: String
    ) {
        launch {
            playlistRepository.renamePlaylist(playlist, name)
        }
    }
}
