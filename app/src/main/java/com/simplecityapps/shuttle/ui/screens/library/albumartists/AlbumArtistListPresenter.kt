package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

class AlbumArtistListPresenter @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository
) : AlbumArtistListContract.Presenter, BasePresenter<AlbumArtistListContract.View>() {

    override fun loadAlbumArtists() {
        addDisposable(
            albumArtistRepository.getAlbumArtists()
                .map { albumArtist -> albumArtist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.sortKey, b.sortKey) }) }
                .subscribeBy(
                    onNext = { albumArtists -> view?.setAlbumArtists(albumArtists) },
                    onError = { error -> Timber.e(error, "Failed to retrieve album artists") })
        )
    }
}