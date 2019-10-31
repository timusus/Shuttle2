package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist.Companion.MostPlayed
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist.Companion.RecentlyPlayed
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

interface HomeContract {

    interface View {
        fun showLoadError(error: Error)
        fun setMostPlayed(songs: List<Song>)
        fun setRecentlyPlayed(songs: List<Song>)
        fun onAddedToQueue(song: Song)
    }

    interface Presenter {
        fun shuffleAll()
        fun play(song: Song, songs: List<Song>)
        fun loadMostPlayed()
        fun loadRecentlyPlayed()
        fun addToQueue(song: Song)
    }
}

class HomePresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager

) : HomeContract.Presenter, BasePresenter<HomeContract.View>() {

    override fun shuffleAll() {
        addDisposable(songRepository.getSongs()
            .first(emptyList())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { songs ->
                    playbackManager.load(songs, Random.nextInt(songs.size)) { result ->
                        result.onSuccess {
                            queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
                            playbackManager.play()
                        }
                        result.onFailure { error -> view?.showLoadError(Error(error)) }
                    }
                },
                onError = { throwable -> Timber.e(throwable, "Error retrieving songs") }
            ))
    }

    override fun play(song: Song, songs: List<Song>) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess {
                playbackManager.play()
            }
            result.onFailure { error -> view?.showLoadError(Error(error)) }
        }
    }

    override fun loadMostPlayed() {
        addDisposable(
            songRepository.getSongs(MostPlayed.songQuery)
                .map { songs -> MostPlayed.songQuery?.sortOrder?.let { songs.sortedWith(it.getSortOrder()) } ?: songs }
                .flatMap { songs -> Observable.just(songs.take(20)) }
                .subscribeBy(
                    onNext = { songs ->
                        if (songs.count() >= 4) {
                            view?.setMostPlayed(songs)
                        }
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to load most played songs") }
                )
        )
    }

    override fun loadRecentlyPlayed() {
        addDisposable(
            songRepository.getSongs(RecentlyPlayed.songQuery)
                .map { songs -> RecentlyPlayed.songQuery?.sortOrder?.let { songSortOrder -> songs.sortedWith(songSortOrder.getSortOrder()) } ?: songs }
                .flatMap { songs -> Observable.just(songs.take(5)) }
                .subscribeBy(
                    onNext = { songs ->
                        if (songs.count() >= 3) {
                            view?.setRecentlyPlayed(songs)
                        }
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to load most recently played songs") }
                )
        )
    }

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song)
    }
}