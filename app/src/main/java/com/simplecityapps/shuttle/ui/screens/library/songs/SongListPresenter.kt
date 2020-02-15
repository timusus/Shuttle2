package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

interface SongListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadSongs()
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun rescanLibrary()
    }
}

class SongListPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val songRepository: SongRepository,
    private val mediaImporter: MediaImporter
) : BasePresenter<SongListContract.View>(),
    SongListContract.Presenter {

    var songs: List<Song> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, message: String) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadSongs() {
        addDisposable(
            songRepository.getSongs()
                .map { song -> song.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.name, b.name) }) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { songs ->
                        this.songs = songs
                        if (this.songs.isEmpty()) {
                            if (mediaImporter.isScanning) {
                                mediaImporter.listeners.add(mediaImporterListener)
                                view?.setLoadingState(SongListContract.LoadingState.Scanning)
                            } else {
                                mediaImporter.listeners.remove(mediaImporterListener)
                                view?.setLoadingState(SongListContract.LoadingState.Empty)
                            }
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(SongListContract.LoadingState.None)
                        }
                        view?.setData(songs)
                    },
                    onError = { error -> Timber.e(error, "Failed to load songs") }
                )
        )
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess {
                playbackManager.play()
            }
            result.onFailure { error ->
                view?.showLoadError(error as Error)
            }
        }
    }

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song)
    }

    override fun playNext(song: Song) {
        playbackManager.playNext(listOf(song))
        view?.onAddedToQueue(song)
    }

    override fun rescanLibrary() {
        mediaImporter.rescan()
    }
}