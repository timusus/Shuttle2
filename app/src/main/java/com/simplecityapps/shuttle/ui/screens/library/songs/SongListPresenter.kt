package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject

class SongListPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val songRepository: SongRepository
) : BasePresenter<SongListContract.View>(),
    SongListContract.Presenter {

    var songs: List<Song> = emptyList()

    override fun loadSongs() {
        addDisposable(
            songRepository.getSongs()
                .map { albumArtist -> albumArtist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.name, b.name) }) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { songs ->
                        this.songs = songs
                        view?.setData(songs)
                    },
                    onError = { error -> Timber.e(error, "Failed to load songs") }
                )
        )
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }
}