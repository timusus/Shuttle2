package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

interface AlbumArtistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbumArtists(albumArtists: List<AlbumArtist>)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
    }

    interface Presenter {
        fun loadAlbumArtists()
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun rescanLibrary()
    }
}

class AlbumArtistListPresenter @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter
) : AlbumArtistListContract.Presenter,
    BasePresenter<AlbumArtistListContract.View>() {

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, message: String) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadAlbumArtists() {
        addDisposable(
            albumArtistRepository.getAlbumArtists()
                .map { albumArtist -> albumArtist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.sortKey, b.sortKey) }) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { albumArtists ->
                        if (albumArtists.isEmpty()) {
                            if (mediaImporter.isScanning) {
                                mediaImporter.listeners.add(mediaImporterListener)
                                view?.setLoadingState(AlbumArtistListContract.LoadingState.Scanning)
                            } else {
                                mediaImporter.listeners.remove(mediaImporterListener)
                                view?.setLoadingState(AlbumArtistListContract.LoadingState.Empty)
                            }
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(AlbumArtistListContract.LoadingState.None)
                        }
                        view?.setAlbumArtists(albumArtists)
                    },
                    onError = { error -> Timber.e(error, "Failed to retrieve album artists") })
        )
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.addToQueue(songs)
                        view?.onAddedToQueue(albumArtist)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
        )
    }

    override fun playNext(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.playNext(songs)
                        view?.onAddedToQueue(albumArtist)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
        )
    }

    override fun rescanLibrary() {
        mediaImporter.rescan()
    }
}