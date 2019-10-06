package com.simplecityapps.shuttle.ui.screens.home.history

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HistoryPresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager
) : BasePresenter<HistoryContract.View>(), HistoryContract.Presenter {

    var songs: List<Song> = emptyList()

    override fun loadHistory() {
        addDisposable(
            songRepository.getSongs(SongQuery.LastCompleted(Date(Date().time - TimeUnit.DAYS.toMillis(7))))
                .map { songs -> songs.sortedBy { song -> song.lastCompleted }.reversed() }
                .subscribeBy(
                    onNext = { songs ->
                        this.songs = songs
                        view?.setData(songs)
                    },
                    onError = { error -> Timber.e(error, "Failed to load history") })
        )
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
}