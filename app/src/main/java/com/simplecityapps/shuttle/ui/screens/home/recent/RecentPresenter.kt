package com.simplecityapps.shuttle.ui.screens.home.recent

import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RecentPresenter @Inject constructor(
    private val songRepository: SongRepository
) : BasePresenter<RecentContract.View>(), RecentContract.Presenter {

    override fun loadRecent() {
        addDisposable(
            songRepository.getSongs(SongQuery.LastPlayed(Date(Date().time - TimeUnit.DAYS.toMillis(7))))
                .map { songs -> songs.sortedBy { it.lastPlayed }.reversed() }
                .subscribeBy(
                    onNext = { songs -> view?.setData(songs) },
                    onError = { error -> Timber.e(error, "Failed to load recent") })
        )
    }
}