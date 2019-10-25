package com.simplecityapps.shuttle.ui.screens.library.playlists.smart

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.Serializable
import kotlin.random.Random

data class SmartPlaylist(val nameResId: Int, val songQuery: SongQuery?) : Serializable {

    companion object {
        val MostPlayed = SmartPlaylist(
            R.string.playlist_title_most_played,
            SongQuery.PlayCount(2, Comparator { a, b -> b.playCount.compareTo(a.playCount) })
        )
        val RecentlyPlayed = SmartPlaylist(
            R.string.playlist_title_recently_played,
            SongQuery.PlayCount(1, Comparator { a, b -> b.lastPlayed?.compareTo(a.lastPlayed) ?: 0 })
        )
    }
}

interface SmartPlaylistDetailContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song)
        fun shuffle()
        fun addToQueue(song: Song)
    }
}

class SmartPlaylistDetailPresenter @AssistedInject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val playlist: SmartPlaylist
) : BasePresenter<SmartPlaylistDetailContract.View>(),
    SmartPlaylistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(playlist: SmartPlaylist): SmartPlaylistDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun loadData() {
        addDisposable(songRepository.getSongs(playlist.songQuery)
            .map { songs -> playlist.songQuery?.sortOrder?.let { songs.sortedWith(it) } ?: songs }
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

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song)
    }
}