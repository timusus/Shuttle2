package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.model.removeArticles
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AlbumListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbums(albums: List<Album>, viewMode: ViewMode, resetPosition: Boolean)
        fun updateSortOrder(sortOrder: AlbumSortOrder)
        fun onAddedToQueue(album: Album)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showLoadError(error: Error)
        fun showTagEditor(songs: List<Song>)
        fun setViewMode(viewMode: ViewMode)
    }

    interface Presenter {
        fun loadAlbums(resetPosition: Boolean)
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun exclude(album: Album)
        fun editTags(album: Album)
        fun play(album: Album)
        fun toggleViewMode()
        fun albumShuffle()
        fun setSortOrder(albumSortOrder: AlbumSortOrder)
        fun updateSortOrder()
        fun getFastscrollPrefix(album: Album): String?
    }
}

class AlbumListPresenter @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
    private val sortPreferenceManager: SortPreferenceManager
) : AlbumListContract.Presenter,
    BasePresenter<AlbumListContract.View>() {

    private var albums: List<Album> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Int, total: Int, song: Song) {
            view?.setLoadingProgress(progress / total.toFloat())
        }
    }

    override fun bindView(view: AlbumListContract.View) {
        super.bindView(view)

        view.updateSortOrder(sortPreferenceManager.sortOrderAlbumList)
        view.setViewMode(preferenceManager.albumListViewMode.toViewMode())
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadAlbums(resetPosition: Boolean) {
        launch {
            albumRepository.getAlbums(AlbumQuery.All(sortOrder = sortPreferenceManager.sortOrderAlbumList))
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
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
                    view?.setAlbums(albums, preferenceManager.albumListViewMode.toViewMode(), resetPosition)
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

    override fun toggleViewMode() {
        val viewMode = when (preferenceManager.albumListViewMode.toViewMode()) {
            ViewMode.List -> ViewMode.Grid
            ViewMode.Grid -> ViewMode.List
        }
        preferenceManager.albumListViewMode = viewMode.name
        view?.setViewMode(viewMode)
        view?.setAlbums(albums, viewMode, false)
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

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            view?.showTagEditor(songs)
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

    override fun albumShuffle() {
        launch {
            val albums = songRepository
                .getSongs(SongQuery.All())
                .firstOrNull()
                .orEmpty()
                .groupBy { it.album }

            val songs = albums.keys.shuffled().flatMap { key ->
                albums.getValue(key)
            }

            playbackManager.load(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun setSortOrder(albumSortOrder: AlbumSortOrder) {
        if (sortPreferenceManager.sortOrderAlbumList != albumSortOrder) {
            launch {
                withContext(Dispatchers.IO) {
                    sortPreferenceManager.sortOrderAlbumList = albumSortOrder
                    this@AlbumListPresenter.albums = albums.sortedWith(albumSortOrder.comparator)
                }
                view?.setAlbums(albums, preferenceManager.albumListViewMode.toViewMode(), true)
                view?.updateSortOrder(albumSortOrder)
            }
        }
    }

    override fun updateSortOrder() {
        view?.updateSortOrder(sortPreferenceManager.sortOrderAlbumList)
    }

    override fun getFastscrollPrefix(album: Album): String? {
        return when (sortPreferenceManager.sortOrderAlbumList) {
            AlbumSortOrder.AlbumName -> album.sortKey?.firstOrNull().toString()
            AlbumSortOrder.ArtistName -> album.albumArtist.removeArticles().firstOrNull().toString()
            AlbumSortOrder.Year -> album.year.toString()
            else -> null
        }
    }
}