package com.simplecityapps.shuttle.ui.screens.library.songs

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

interface SongListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Loading : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setData(songs: List<Song>, resetPosition: Boolean)
        fun updateToolbarMenuSortOrder(sortOrder: SongSortOrder)
        fun showLoadError(error: Error)
        fun onAddedToQueue(songs: List<Song>)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Progress?)
        fun showDeleteError(error: Error)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadSongs(resetPosition: Boolean)
        fun onSongClicked(song: Song)
        fun addToQueue(songs: List<Song>)
        fun playNext(song: Song)
        fun exclude(song: Song)
        fun delete(song: Song)
        fun setSortOrder(songSortOrder: SongSortOrder)
        fun getFastscrollPrefix(song: Song): String?
        fun updateToolbarMenu()
        fun shuffle()
    }
}

class SongListPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: PlaybackManager,
    private val songRepository: SongRepository,
    private val mediaImporter: MediaImporter,
    private val sortPreferenceManager: SortPreferenceManager,
    private val queueManager: QueueManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : BasePresenter<SongListContract.View>(),
    SongListContract.Presenter {

    var songs: List<Song> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onSongImportProgress(providerType: MediaProviderType, message: String, progress: Progress?) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun bindView(view: SongListContract.View) {
        super.bindView(view)

        view.updateToolbarMenuSortOrder(sortPreferenceManager.sortOrderSongList)
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadSongs(resetPosition: Boolean) {
        if (songs.isEmpty()) {
            if (mediaImporter.isImporting) {
                view?.setLoadingState(SongListContract.LoadingState.Scanning)
            } else {
                view?.setLoadingState(SongListContract.LoadingState.Loading)
            }
        }
        launch {
            songRepository
                .getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList))
                .filterNotNull()
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collect { songs ->
                    this@SongListPresenter.songs = songs
                    if (songs.isEmpty()) {
                        if (mediaImporter.isImporting) {
                            mediaImporter.listeners.add(mediaImporterListener)
                            view?.setLoadingState(SongListContract.LoadingState.Scanning)
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(SongListContract.LoadingState.Empty)
                        }
                    } else {
                        mediaImporter.listeners.remove(mediaImporterListener)
                        view?.setLoadingState(SongListContract.LoadingState.None)
                    }
                    view?.setData(songs, resetPosition)
                }
        }
    }

    override fun onSongClicked(song: Song) {
        launch {
            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess {
                        playbackManager.play()
                    }
                    result.onFailure { error ->
                        view?.showLoadError(error as Error)
                    }
                }
            }
        }
    }

    override fun addToQueue(songs: List<Song>) {
        launch {
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(songs)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(listOf(song))
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(queueManager.getQueue().filter { queueItem -> songs.contains(queueItem.song) })
        }
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(song)
                queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
            }
        } else {
            view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
        }
    }

    override fun setSortOrder(songSortOrder: SongSortOrder) {
        if (sortPreferenceManager.sortOrderSongList != songSortOrder) {
            launch {
                withContext(Dispatchers.IO) {
                    sortPreferenceManager.sortOrderSongList = songSortOrder
                    this@SongListPresenter.songs = songs.sortedWith(songSortOrder.comparator)
                }
                view?.setData(songs, true)
                view?.updateToolbarMenuSortOrder(songSortOrder)
            }
        }
    }

    override fun getFastscrollPrefix(song: Song): String? {
        return when (sortPreferenceManager.sortOrderSongList) {
            SongSortOrder.SongName -> song.name?.firstOrNull()?.toString()
            SongSortOrder.ArtistGroupKey -> song.albumArtistGroupKey.key?.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault())
            SongSortOrder.AlbumGroupKey -> song.albumGroupKey.key?.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault())
            SongSortOrder.Year -> song.date?.year?.toString()
            else -> null
        }
    }

    override fun shuffle() {
        if (songs.isEmpty()) {
            view?.showLoadError(UserFriendlyError("Your library is empty"))
            return
        }

        appCoroutineScope.launch {
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(Error(error)) }
            }
        }
    }

    override fun updateToolbarMenu() {
        view?.updateToolbarMenuSortOrder(sortPreferenceManager.sortOrderSongList)
    }
}
