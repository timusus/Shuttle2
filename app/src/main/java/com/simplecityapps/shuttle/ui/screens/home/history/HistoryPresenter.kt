package com.simplecityapps.shuttle.ui.screens.home.history

import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HistoryPresenter @Inject constructor(
    private val songRepository: SongRepository
) : BasePresenter<HistoryContract.View>(), HistoryContract.Presenter {

    override fun loadHistory() {
        addDisposable(
            songRepository.getSongs(SongQuery.LastCompleted(Date(Date().time - TimeUnit.DAYS.toMillis(7))))
                .map { songs -> songs.sortedBy { song -> song.lastCompleted }.reversed() }
                .subscribeBy(
                    onNext = { songs -> view?.setData(songs) },
                    onError = { error -> Timber.e(error, "Failed to load history") })
        )
    }
}