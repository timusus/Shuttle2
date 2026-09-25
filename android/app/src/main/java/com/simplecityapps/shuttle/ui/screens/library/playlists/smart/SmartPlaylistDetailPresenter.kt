package com.simplecityapps.shuttle.ui.screens.library.playlists.smart

import android.content.Context
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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

        fun onAddedToQueue(playlist: com.simplecityapps.shuttle.model.SmartPlaylist)

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

        fun addToQueue(playlist: com.simplecityapps.shuttle.model.SmartPlaylist)
    }
}

class SmartPlaylistDetailPresenter
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    @Assisted private val playlist: com.simplecityapps.shuttle.model.SmartPlaylist
) : BasePresenter<SmartPlaylistDetailContract.View>(),
    SmartPlaylistDetailContract.Presenter {
    @AssistedFactory
    interface Factory {
        fun create(playlist: com.simplecityapps.shuttle.model.SmartPlaylist): SmartPlaylistDetailPresenter
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
            val result = playSongs(songs, songs.indexOf(song).coerceAtLeast(0))
            if (result is PlaySongs.Result.Failure && result.message != null) view?.showLoadError(Error(result.message))
        }
    }

    override fun shuffle() {
        if (songs.isNotEmpty()) {
            launch {
                val result = shuffleSongs(songs)
                if (result is ShuffleSongs.Result.Failure) view?.showLoadError(Error(result.message))
            }
        } else {
            Timber.i("Shuffle failed: Songs list empty")
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.End)
            view?.onAddedToQueue(song)
        }
    }

    override fun addToQueue(playlist: com.simplecityapps.shuttle.model.SmartPlaylist) {
        launch {
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.End)
            view?.onAddedToQueue(playlist)
        }
    }

    override fun playNext(song: Song) {
        launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.Next)
            view?.onAddedToQueue(song)
        }
    }

    override fun exclude(song: Song) {
        launch { excludeSongs(MediaSelection.Songs(song)) }
    }

    override fun delete(song: Song) {
        launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) {
                view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
            }
        }
    }
}
