package com.simplecityapps.shuttle.ui.screens.library.playlists.smart

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

interface SmartPlaylistDetailContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
        fun onAddedToQueue(playlist: SmartPlaylist)
        fun showDeleteError(error: Error)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song)
        fun shuffle()
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun exclude(song: Song)
        fun delete(song: Song)
        fun addToQueue(playlist: SmartPlaylist)
    }
}

class SmartPlaylistDetailPresenter @AssistedInject constructor(
    @ApplicationContext private val context: Context,
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
        launch {
            songRepository.getSongs(playlist.songQuery)
                .filterNotNull()
                .map { songs -> playlist.songQuery.sortOrder.let { sortOrder -> songs.sortedWith(sortOrder.comparator) } }
                .collect { songs ->
                    this@SmartPlaylistDetailPresenter.songs = songs
                    view?.setData(songs)
                }
        }
    }

    override fun onSongClicked(song: Song) {
        launch {
            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun shuffle() {
        if (songs.isNotEmpty()) {
            launch {
                playbackManager.shuffle(songs) { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        } else {
            Timber.i("Shuffle failed: Songs list empty")
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun addToQueue(playlist: SmartPlaylist) {
        launch {
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(queueManager.getQueue().filter { it.song == song })
        }
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(song)
                queueManager.remove(queueManager.getQueue().filter { it.song == song })
            }
        } else {
            view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
        }
    }
}