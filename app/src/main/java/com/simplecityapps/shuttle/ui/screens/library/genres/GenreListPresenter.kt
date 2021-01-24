package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class GenreListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Loading : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setGenres(genres: List<Genre>, resetPosition: Boolean)
        fun onAddedToQueue(genre: Genre)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Float)
        fun showLoadError(error: Error)
        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter {
        fun loadGenres(resetPosition: Boolean)
        fun addToQueue(genre: Genre)
        fun playNext(genre: Genre)
        fun exclude(genre: Genre)
        fun editTags(genre: Genre)
        fun play(genre: Genre)
        fun getFastscrollPrefix(genre: Genre): String?
    }
}

class GenreListPresenter @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val queueManager: QueueManager
) : GenreListContract.Presenter,
    BasePresenter<GenreListContract.View>() {

    private var genres: List<Genre>? = null

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onProgress(providerType: MediaProvider.Type, progress: Int, total: Int, song: Song) {
            view?.setLoadingProgress(progress / total.toFloat())
        }
    }

    override fun unbindView() {
        super.unbindView()

        mediaImporter.listeners.remove(mediaImporterListener)
    }

    override fun loadGenres(resetPosition: Boolean) {
        if (genres == null) {
            if (mediaImporter.isImporting) {
                view?.setLoadingState(GenreListContract.LoadingState.Scanning)
            } else {
                view?.setLoadingState(GenreListContract.LoadingState.Loading)
            }
        }
        launch {
            genreRepository.getGenres(GenreQuery.All())
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collect { genres ->
                    if (genres.isEmpty()) {
                        if (mediaImporter.isImporting) {
                            mediaImporter.listeners.add(mediaImporterListener)
                            view?.setLoadingState(GenreListContract.LoadingState.Scanning)
                        } else {
                            mediaImporter.listeners.remove(mediaImporterListener)
                            view?.setLoadingState(GenreListContract.LoadingState.Empty)
                        }
                    } else {
                        mediaImporter.listeners.remove(mediaImporterListener)
                        view?.setLoadingState(GenreListContract.LoadingState.None)
                    }
                    this@GenreListPresenter.genres = genres
                    view?.setGenres(genres, resetPosition)
                }
        }
    }

    override fun addToQueue(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .firstOrNull()
                .orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(genre)
        }
    }

    override fun playNext(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .firstOrNull()
                .orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(genre)
        }
    }

    override fun exclude(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .firstOrNull()
                .orEmpty()
            songRepository.setExcluded(songs, true)
            queueManager.remove(queueManager.getQueue().filter { queueItem -> songs.contains(queueItem.song) })
        }
    }

    override fun editTags(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .firstOrNull()
                .orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun play(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .firstOrNull()
                .orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun getFastscrollPrefix(genre: Genre): String? {
        return genre.name.first().toString()
    }
}