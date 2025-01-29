package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AlbumListContract {
    sealed class LoadingState {
        object Scanning : LoadingState()

        object Loading : LoadingState()

        object Empty : LoadingState()

        object None : LoadingState()
    }

    interface View {
        fun setAlbums(
            albums: List<Album>,
            viewMode: ViewMode,
            resetPosition: Boolean
        )

        fun updateToolbarMenuSortOrder(sortOrder: AlbumSortOrder)

        fun updateToolbarMenuViewMode(viewMode: ViewMode)

        fun onAddedToQueue(albums: List<Album>)

        fun setLoadingState(state: LoadingState)

        fun setLoadingProgress(progress: Float)

        fun showLoadError(error: Error)

        fun showTagEditor(songs: List<Song>)

        fun setViewMode(viewMode: ViewMode)
    }

    interface Presenter {
        fun loadAlbums(resetPosition: Boolean)

        fun addToQueue(albums: List<Album>)

        fun playNext(album: Album)

        fun exclude(album: Album)

        fun editTags(albums: List<Album>)

        fun play(album: Album)

        fun setViewMode(viewMode: ViewMode)

        fun albumShuffle()

        fun setSortOrder(albumSortOrder: AlbumSortOrder)

        fun getFastscrollPrefix(album: Album): String?

        fun updateToolbarMenu()
    }
}

class AlbumListPresenter
@Inject
constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val preferenceManager: GeneralPreferenceManager,
    private val sortPreferenceManager: SortPreferenceManager,
    private val queueManager: QueueManager
) : BasePresenter<AlbumListContract.View>(),
    AlbumListContract.Presenter {
    private var albums: List<Album> = emptyList()

    private val mediaImporterListener =
        object : MediaImporter.Listener {
            override fun onSongImportProgress(
                providerType: MediaProviderType,
                message: String,
                progress: Progress?
            ) {
                progress?.let {
                    view?.setLoadingProgress(progress.asFloat())
                }
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

    override fun loadAlbums(resetPosition: Boolean) {
        if (albums.isEmpty()) {
            if (mediaImporter.isImporting) {
                view?.setLoadingState(AlbumListContract.LoadingState.Scanning)
            } else {
                view?.setLoadingState(AlbumListContract.LoadingState.Loading)
            }
        }
        launch {
            Timber.i("Loading with sort order: ${sortPreferenceManager.sortOrderAlbumList}")
            albumRepository.getAlbums(AlbumQuery.All(sortOrder = sortPreferenceManager.sortOrderAlbumList))
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
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

    override fun addToQueue(albums: List<Album>) {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.AlbumGroupKeys(albums.map { SongQuery.AlbumGroupKey(key = it.groupKey) }))
                    .firstOrNull()
                    .orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albums)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey))))
                    .firstOrNull()
                    .orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(listOf(album))
        }
    }

    override fun setViewMode(viewMode: ViewMode) {
        preferenceManager.albumListViewMode = viewMode.name
        view?.setViewMode(viewMode)
        view?.setAlbums(albums, viewMode, false)
        view?.updateToolbarMenuViewMode(viewMode)
    }

    override fun exclude(album: Album) {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey))))
                    .firstOrNull()
                    .orEmpty()
            songRepository.setExcluded(songs, true)
            queueManager.remove(queueManager.getQueue().filter { queueItem -> songs.contains(queueItem.song) })
        }
    }

    override fun editTags(albums: List<Album>) {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.AlbumGroupKeys(albums.map { album -> SongQuery.AlbumGroupKey(key = album.groupKey) }))
                    .firstOrNull()
                    .orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun play(album: Album) {
        launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey))))
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

    override fun albumShuffle() {
        launch {
            val albums =
                songRepository
                    .getSongs(SongQuery.All())
                    .firstOrNull()
                    .orEmpty()
                    .groupBy { it.album }

            val songs =
                albums.keys.shuffled().flatMap { key ->
                    albums.getValue(key)
                }

            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun setSortOrder(albumSortOrder: AlbumSortOrder) {
        if (sortPreferenceManager.sortOrderAlbumList != albumSortOrder) {
            Timber.i("Updating sort order: $albumSortOrder")
            launch {
                withContext(Dispatchers.IO) {
                    sortPreferenceManager.sortOrderAlbumList = albumSortOrder
                    albums = albums.sortedWith(albumSortOrder.comparator)
                }
                view?.setAlbums(albums, preferenceManager.albumListViewMode.toViewMode(), true)
                view?.updateToolbarMenuSortOrder(albumSortOrder)
            }
        }
    }

    override fun updateToolbarMenu() {
        view?.updateToolbarMenuSortOrder(sortPreferenceManager.sortOrderAlbumList)
        view?.updateToolbarMenuViewMode(preferenceManager.albumListViewMode.toViewMode())
    }

    override fun getFastscrollPrefix(album: Album): String? = when (sortPreferenceManager.sortOrderAlbumList) {
        AlbumSortOrder.AlbumName -> album.groupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
        AlbumSortOrder.ArtistGroupKey -> album.groupKey?.albumArtistGroupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
        AlbumSortOrder.Year -> album.year.toString()
        else -> null
    }
}
