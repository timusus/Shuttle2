package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.Collator
import javax.inject.Inject

class AlbumListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbums(albums: List<Album>)
        fun onAddedToQueue(album: Album)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showLoadError(error: Error)
    }

    interface Presenter {
        fun loadAlbums()
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun rescanLibrary()
        fun blacklist(album: Album)
        fun play(album: Album)
    }
}

class AlbumListPresenter @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter
) : AlbumListContract.Presenter,
    BasePresenter<AlbumListContract.View>() {

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(progress: Float, song: Song) {
            view?.setLoadingProgress(progress)
        }
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
                    view?.setAlbums(albums)
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
        mediaImporter.reImport()
    }

    override fun blacklist(album: Album) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist))))
                .firstOrNull()
                .orEmpty()
            songRepository.setBlacklisted(songs, true)
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