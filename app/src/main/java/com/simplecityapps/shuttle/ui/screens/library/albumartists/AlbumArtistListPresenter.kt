package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject
import javax.inject.Named

interface AlbumArtistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbumArtists(albumArtists: List<AlbumArtist>, viewMode: ViewMode)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showLoadError(error: Error)
        fun setViewMode(viewMode: ViewMode)
    }

    interface Presenter {
        fun loadAlbumArtists()
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun rescanLibrary()
        fun exclude(albumArtist: AlbumArtist)
        fun play(albumArtist: AlbumArtist)
        fun toggleViewMode()
    }
}

class AlbumArtistListPresenter @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AlbumArtistListContract.Presenter,
    BasePresenter<AlbumArtistListContract.View>() {

    private var albumArtists: List<AlbumArtist> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, song: Song) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun bindView(view: AlbumArtistListContract.View) {
        super.bindView(view)

        view.setViewMode(preferenceManager.artistListViewMode.toViewMode())
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadAlbumArtists() {
        launch {
            albumArtistRepository.getAlbumArtists().map { albumArtist -> albumArtist.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.sortKey, b.sortKey) }) }
                .collect { artists ->
                    if (artists.isEmpty()) {
                        if (mediaImporter.isImporting) {
                            mediaImporter.listeners.add(mediaImporterListener)
                            view?.setLoadingState(AlbumArtistListContract.LoadingState.Scanning)
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(AlbumArtistListContract.LoadingState.Empty)
                        }
                    } else {
                        mediaImporter.listeners.remove(mediaImporterListener)
                        view?.setLoadingState(AlbumArtistListContract.LoadingState.None)
                    }
                    this@AlbumArtistListPresenter.albumArtists = artists
                    view?.setViewMode(preferenceManager.artistListViewMode.toViewMode())
                    view?.setAlbumArtists(artists, preferenceManager.artistListViewMode.toViewMode())
                }
        }
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name))))
                .firstOrNull()
                .orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun playNext(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name))))
                .firstOrNull()
                .orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun rescanLibrary() {
        appCoroutineScope.launch {
            mediaImporter.reImport()
        }
    }

    override fun toggleViewMode() {
        val viewMode = when (preferenceManager.artistListViewMode.toViewMode()) {
            ViewMode.List -> ViewMode.Grid
            ViewMode.Grid -> ViewMode.List
        }
        preferenceManager.artistListViewMode = viewMode.name
        loadAlbumArtists() // Intrinsically calls `view?.setViewMode()`
    }

    override fun exclude(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name))))
                .firstOrNull()
                .orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun play(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name))))
                .firstOrNull()
                .orEmpty()
            playbackManager.load(songs, 0) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }
}