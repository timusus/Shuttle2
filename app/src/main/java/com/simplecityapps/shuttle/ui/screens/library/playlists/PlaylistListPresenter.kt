package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

class PlaylistListPresenter @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : PlaylistListContract.Presenter,
    BasePresenter<PlaylistListContract.View>() {

    override fun loadPlaylists() {
        addDisposable(
            playlistRepository.getPlaylists()
                .map { playlist -> playlist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.name, b.name) }) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { playlists -> view?.setPlaylists(playlists) },
                    onError = { error -> Timber.e(error, "Failed to retrieve playlists") }
                )
        )
    }

    override fun deletePlaylist(playlist: Playlist) {
        addDisposable(
            playlistRepository.deletePlaylist(playlist)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onError = { error -> Timber.e(error, "Failed to delete playlist: $playlist") }
                )
        )
    }
}