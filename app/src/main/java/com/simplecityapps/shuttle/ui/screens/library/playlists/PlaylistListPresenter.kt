package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

class PlaylistListPresenter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter
) : PlaylistListContract.Presenter,
    BasePresenter<PlaylistListContract.View>() {

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, message: String) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun loadPlaylists() {
        addDisposable(
            playlistRepository.getPlaylists()
                .map { playlist -> playlist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.name, b.name) }) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { playlists ->
                        if (playlists.isEmpty()) {
                            if (mediaImporter.isScanning) {
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
                    },
                    onError = { error -> Timber.e(error, "Failed to retrieve playlists") }
                )
        )
    }

    override fun deletePlaylist(playlist: Playlist) {
        addDisposable(
            playlistRepository.deletePlaylist(playlist)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onError = { error -> Timber.e(error, "Failed to delete playlist: $playlist") }
                )
        )
    }

    override fun addToQueue(playlist: Playlist) {
        addDisposable(
            playlistRepository.getSongsForPlaylist(playlist.id)
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.addToQueue(songs)
                        view?.onAddedToQueue(playlist)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for playlist: ${playlist.name}") })
        )
    }
}