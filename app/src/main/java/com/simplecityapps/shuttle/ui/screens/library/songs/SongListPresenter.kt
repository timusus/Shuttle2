package com.simplecityapps.shuttle.ui.screens.library.songs

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.Collator
import javax.inject.Inject
import javax.inject.Named

interface SongListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(song: Song)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showDeleteError(error: Error)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadSongs()
        fun onSongClicked(song: Song)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun rescanLibrary()
        fun exclude(song: Song)
        fun delete(song: Song)
    }
}

class SongListPresenter @Inject constructor(
    private val context: Context,
    private val playbackManager: PlaybackManager,
    private val songRepository: SongRepository,
    private val mediaImporter: MediaImporter,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope

) : BasePresenter<SongListContract.View>(),
    SongListContract.Presenter {

    var songs: List<Song> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, song: Song) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadSongs() {
        Timber.i("loadSongs()")
        launch {
            songRepository.getSongs(SongQuery.All())
                .map { songs -> songs.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.name, b.name) }) }
                .flowOn(Dispatchers.IO)
                .collect { songs ->
                    Timber.i("loadSongs collected ${songs.size} songs")
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
                    view?.setData(songs)
                }
        }
    }

    override fun onSongClicked(song: Song) {
        launch {
            playbackManager.load(songs, songs.indexOf(song)) { result ->
                result.onSuccess {
                    playbackManager.play()
                }
                result.onFailure { error ->
                    view?.showLoadError(error as Error)
                }
            }
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

    override fun rescanLibrary() {
        appCoroutineScope.launch {
            mediaImporter.reImport()
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