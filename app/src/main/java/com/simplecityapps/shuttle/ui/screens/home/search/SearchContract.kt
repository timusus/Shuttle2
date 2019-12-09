package com.simplecityapps.shuttle.ui.screens.home.search

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface SearchContract : BaseContract.Presenter<SearchContract.View> {

    interface Presenter {
        fun loadData(query: String)
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
    }

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
    }
}

class SearchPresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager
) :
    BasePresenter<SearchContract.View>(),
    SearchContract.Presenter {

    private var songs: List<Song> = emptyList()

    private var queryDisposable: Disposable? = null

    override fun loadData(query: String) {
        queryDisposable?.dispose()
        queryDisposable = songRepository.getSongs(SongQuery.Search(query))
            .map { songs ->
                songs
                    .asSequence()
                    .sortedBy { it.track }
                    .sortedBy { it.albumArtistName }
                    .sortedBy { it.albumName }
                    .sortedByDescending { it.year }
                    .sortedByDescending { it.name.contains(query, true) }
                    .sortedByDescending { it.albumArtistName.contains(query, true) }
                    .sortedByDescending { it.albumName.contains(query, true) }
                    .toList()
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { songs ->
                    this.songs = songs
                    view?.setData(songs)
                },
                onError = { error -> Timber.e(error, "Failed to load songs") }
            )
        addDisposable(queryDisposable!!)
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
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
}