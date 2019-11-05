package com.simplecityapps.shuttle.ui.screens.settings

import androidx.annotation.NavigationRes
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

interface BottomDrawerSettingsContract {

    interface View {
        fun setData(settingsItems: List<SettingsMenuItem>, currentDestination: Int?)
        fun showLoadError(error: Error)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun shuffleAll()
    }
}

class BottomDrawerSettingsPresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager
) :
    BasePresenter<BottomDrawerSettingsContract.View>(),
    BottomDrawerSettingsContract.Presenter {

    @NavigationRes
    var currentDestinationIdRes: Int? = null
        set(value) {
            field = value
            view?.setData(
                SettingsMenuItem.values().toList(),
                value
            )
        }

    override fun loadData() {
        view?.setData(
            SettingsMenuItem.values().toList(),
            currentDestinationIdRes
        )
    }

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
}