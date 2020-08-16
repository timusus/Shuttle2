package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumRepository
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

class AlbumListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbums(albums: List<Album>, viewMode: ViewMode)
        fun onAddedToQueue(album: Album)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showLoadError(error: Error)
        fun setViewMode(viewMode: ViewMode)
    }

    interface Presenter {
        fun loadAlbums()
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun rescanLibrary()
        fun exclude(album: Album)
        fun play(album: Album)
        fun toggleViewMode()
    }
}

class AlbumListPresenter @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AlbumListContract.Presenter,
    BasePresenter<AlbumListContract.View>() {

    private var albums: List<Album> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, song: Song) {
            view?.setLoadingProgress(progress)
        }
    }

    override fun bindView(view: AlbumListContract.View) {
        super.bindView(view)

        view.setViewMode(preferenceManager.albumListViewMode.toViewMode())
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadAlbums() {
        launch {
            albumRepository.getAlbums()
                .map { albums -> albums.sortedWith(Comparator { a, b -> Collator.getInstance().compare(a.sortKey, b.sortKey) }) }
                .collect { albums ->
                    if (albums.isEmpty()) {
                        if (mediaImporter.isImporting) {
                            mediaImporter.listeners.add(mediaImporterListener)
                            view?.setLoadingState(AlbumListContract.LoadingState.Scanning)
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(AlbumListContract.LoadingState.Empty)
                        }
                    } else {
                        mediaImporter.listeners.remove(mediaImporterListener)
                        view?.setLoadingState(AlbumListContract.LoadingState.None)
                    }
                    this@AlbumListPresenter.albums = albums
                    view?.setViewMode(preferenceManager.albumListViewMode.toViewMode())
                    view?.setAlbums(albums, preferenceManager.albumListViewMode.toViewMode())
                }
        }
    }

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun rescanLibrary() {
        appCoroutineScope.launch {
            mediaImporter.reImport()
        }
    }

    override fun toggleViewMode() {
        val viewMode = when (preferenceManager.albumListViewMode.toViewMode()) {
            ViewMode.List -> ViewMode.Grid
            ViewMode.Grid -> ViewMode.List
        }
        preferenceManager.albumListViewMode = viewMode.name
        view?.setViewMode(viewMode)
        view?.setAlbums(albums, viewMode)
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun play(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            playbackManager.load(songs, 0) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }
}