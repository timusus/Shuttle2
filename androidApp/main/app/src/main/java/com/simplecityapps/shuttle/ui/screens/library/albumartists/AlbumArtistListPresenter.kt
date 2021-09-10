package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

interface AlbumArtistListContract {

    sealed class LoadingState {
        object Scanning : LoadingState()
        object Loading : LoadingState()
        object Empty : LoadingState()
        object None : LoadingState()
    }

    interface View {
        fun setAlbumArtists(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>, viewMode: ViewMode)
        fun onAddedToQueue(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>)
        fun setLoadingState(state: LoadingState)
        fun setLoadingProgress(progress: Progress?)
        fun showLoadError(error: Error)
        fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>)
        fun setViewMode(viewMode: ViewMode)
        fun updateToolbarMenuViewMode(viewMode: ViewMode)
    }

    interface Presenter {
        fun loadAlbumArtists()
        fun addToQueue(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>)
        fun playNext(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist)
        fun exclude(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist)
        fun editTags(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>)
        fun play(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist)
        fun setViewMode(viewMode: ViewMode)
        fun updateToolbarMenu()
    }
}

class AlbumArtistListPresenter @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
    private val queueManager: QueueManager
) : AlbumArtistListContract.Presenter,
    BasePresenter<AlbumArtistListContract.View>() {

    private var albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist> = emptyList()

    private val mediaImporterListener = object : MediaImporter.Listener {
        override fun onSongImportProgress(providerType: MediaProviderType, message: String, progress: Progress?) {
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
        if (albumArtists.isEmpty()) {
            if (mediaImporter.isImporting) {
                view?.setLoadingState(AlbumArtistListContract.LoadingState.Scanning)
            } else {
                view?.setLoadingState(AlbumArtistListContract.LoadingState.Loading)
            }
        }
        launch {
            albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All())
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
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

    override fun addToQueue(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.ArtistGroupKeys(albumArtists.map { albumArtist -> SongQuery.ArtistGroupKey(key = albumArtist.groupKey) }))
                .firstOrNull()
                .orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtists)
        }
    }

    override fun playNext(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
                .firstOrNull()
                .orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(listOf(albumArtist))
        }
    }

    override fun setViewMode(viewMode: ViewMode) {
        preferenceManager.artistListViewMode = viewMode.name
        loadAlbumArtists() // Intrinsically calls `view?.setViewMode()`
        view?.updateToolbarMenuViewMode(viewMode)
    }

    override fun exclude(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
                .firstOrNull()
                .orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun editTags(albumArtists: List<com.simplecityapps.shuttle.model.AlbumArtist>) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(albumArtists.map { albumArtist -> SongQuery.ArtistGroupKey(key = albumArtist.groupKey) })).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun play(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) {
        launch {
            val songs = songRepository
                .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
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

    override fun updateToolbarMenu() {
        view?.updateToolbarMenuViewMode(preferenceManager.artistListViewMode.toViewMode())

    }
}