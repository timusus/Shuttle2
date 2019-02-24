package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.repository.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import timber.log.Timber

class AlbumArtistDetailPresenter @AssistedInject constructor(
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    @Assisted private val albumArtistId: Long
) : BasePresenter<AlbumArtistDetailContract.View>(), AlbumArtistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(albumArtistId: Long): AlbumArtistDetailPresenter
    }

    override fun bindView(view: AlbumArtistDetailContract.View) {
        super.bindView(view)

        addDisposable(albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistId(albumArtistId)).firstOrError()
            .map { it.firstOrNull() }
            .subscribe(
                { albumArtist ->
                    albumArtist?.let {
                        view.setTitle(albumArtist.name)
                    }
                },
                { error -> Timber.e(error, "Failed to retrieve name for album artist $albumArtistId") })
        )
    }

    override fun loadData() {
        addDisposable(albumRepository.getAlbums(AlbumQuery.AlbumArtistId(albumArtistId)).subscribe { albums ->
            view?.setData(albums)
        })
    }
}