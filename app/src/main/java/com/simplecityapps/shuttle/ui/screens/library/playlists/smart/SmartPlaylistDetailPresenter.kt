package com.simplecityapps.shuttle.ui.screens.library.playlists.smart

import android.content.Context
import androidx.annotation.Keep
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.mediaprovider.repository.SongSortOrder
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Serializable

@Keep
data class SmartPlaylist(val nameResId: Int, val songQuery: SongQuery) : Serializable {

    companion object {
        val MostPlayedAlbums = SmartPlaylist(
            R.string.playlist_title_most_played,
            SongQuery.PlayCount(2, SongSortOrder.PlayCount)
        )
        val RecentlyPlayed = SmartPlaylist(
            R.string.playlist_title_recently_played,
            SongQuery.PlayCount(1, SongSortOrder.RecentlyPlayed)
        )
        val RecentlyAdded = SmartPlaylist(
            R.string.btn_recently_added,
            SongQuery.RecentlyAdded()
        )
    }
}

interface SmartPlaylistDetailContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
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
    }
}

class SmartPlaylistDetailPresenter @AssistedInject constructor(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
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
            songRepository.getSongs(playlist.songQuery).map { songs -> playlist.songQuery?.sortOrder?.let { sortOrder -> songs.sortedWith(sortOrder.comparator) } ?: songs }
                .collect { songs ->
                    this@SmartPlaylistDetailPresenter.songs = songs
                    view?.setData(songs)
                }
        }
    }

    override fun onSongClicked(song: Song) {
        launch {
            playbackManager.load(songs, songs.indexOf(song)) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
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

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.removeSong(song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError("The song couldn't be deleted"))
        }
    }
}