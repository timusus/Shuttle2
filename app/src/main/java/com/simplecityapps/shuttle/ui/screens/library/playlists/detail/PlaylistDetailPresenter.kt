package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import kotlin.random.Random

class PlaylistDetailPresenter @AssistedInject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val playlist: Playlist
) : BasePresenter<PlaylistDetailContract.View>(),
    PlaylistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(playlist: Playlist): PlaylistDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun loadData() {
        addDisposable(playlistRepository.getSongsForPlaylist(playlist.id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { songs ->
                this.songs = songs
                view?.setData(songs)
            })
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }

    override fun shuffle() {
        if (songs.isNotEmpty()) {
            queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
            playbackManager.load(songs, Random.nextInt(songs.size)) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        } else {
            Timber.i("Shuffle failed: Songs list empty")
        }
    }
}