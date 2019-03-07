package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class SongsPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val songRepository: SongRepository
) : BasePresenter<SongsContract.View>(),
    SongsContract.Presenter {

    var songs: List<Song> = emptyList()

    override fun bindView(view: SongsContract.View) {
        super.bindView(view)

        loadSongs()
    }

    override fun loadSongs() {
        addDisposable(songRepository.getSongs().subscribeBy(
            onNext = { songs ->
                this.songs = songs
                view?.setData(songs)
            },
            onError = { error -> Timber.e(error, "Failed to load songs") }
        ))
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song), true)
    }
}