package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStorePlaylistImporter
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject

interface PlaylistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setPlaylists(playlists: List<Playlist>)
        fun onAddedToQueue(playlist: Playlist)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun onPlaylistsImported()
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadPlaylists()
        fun addToQueue(playlist: Playlist)
        fun playNext(playlist: Playlist)
        fun deletePlaylist(playlist: Playlist)
        fun importMediaStorePlaylists()
        fun clearPlaylist(playlist: Playlist)
    }
}

class PlaylistListPresenter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val playlistImporter: MediaStorePlaylistImporter
) : PlaylistListContract.Presenter,
    BasePresenter<PlaylistListContract.View>() {

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Int, total: Int, song: Song) {
            view?.setLoadingProgress(progress / total.toFloat())
        }
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadPlaylists() {
        launch {
            playlistRepository.getPlaylists(PlaylistQuery.All())
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collect { playlists ->
                    if (playlists.isEmpty()) {
                        if (mediaImporter.isImporting) {
                            mediaImporter.listeners.add(mediaImporterListener)
                            view?.setLoadingState(PlaylistListContract.LoadingState.Scanning)
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(PlaylistListContract.LoadingState.Empty)
                        }
                    } else {
                        mediaImporter.listeners.remove(mediaImporterListener)
                        view?.setLoadingState(PlaylistListContract.LoadingState.None)
                    }
                    view?.setPlaylists(playlists)
                }
        }
    }

    override fun deletePlaylist(playlist: Playlist) {
        launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    override fun addToQueue(playlist: Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist.id).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(playlist: Playlist) {
        launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist.id).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(playlist)
        }
    }

    override fun importMediaStorePlaylists() {
        launch {
            playlistImporter.importPlaylists()
            view?.onPlaylistsImported()
        }
    }

    override fun clearPlaylist(playlist: Playlist) {
        launch {
            playlistRepository.clearPlaylist(playlist)
        }
    }
}