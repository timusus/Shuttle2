package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

class HomePresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager

) : HomeContract.Presenter, BasePresenter<HomeContract.View>() {

    override fun shuffleAll() {
        addDisposable(songRepository.getSongs()
            .first(emptyList())
            .subscribeBy(
                onSuccess = { songs ->
                    queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
                    playbackManager.load(songs, Random.nextInt(songs.size), 0, true)
                },
                onError = { throwable -> Timber.e(throwable, "Error retrieving songs") }
            ))
    }
}