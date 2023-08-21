package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

interface AlbumDetailContract {

    interface View {
        fun setData(songs: List<Song>, currentSong: Song?)
        fun showLoadError(error: Error)
        fun onAddedToQueue(name: String)
        fun setAlbum(album: com.simplecityapps.shuttle.model.Album)
        fun showDeleteError(error: Error)
        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song)
        fun shuffle()
        fun addToQueue(album: com.simplecityapps.shuttle.model.Album)
        fun addToQueue(song: Song)
        fun playNext(album: com.simplecityapps.shuttle.model.Album)
        fun playNext(song: Song)
        fun getCurrentSong(): Song?
        fun exclude(song: Song)
        fun editTags(song: Song)
        fun editTags(album: com.simplecityapps.shuttle.model.Album)
        fun delete(song: Song)
    }
}

class AlbumDetailPresenter @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    @Assisted private val album: com.simplecityapps.shuttle.model.Album
) : BasePresenter<AlbumDetailContract.View>(),
    AlbumDetailContract.Presenter,
    QueueChangeCallback {

    @AssistedInject.Factory
    interface Factory {
        fun create(album: com.simplecityapps.shuttle.model.Album): AlbumDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun bindView(view: AlbumDetailContract.View) {
        super.bindView(view)

        queueWatcher.addCallback(this)
        view.setAlbum(album)

        launch {
            albumRepository.getAlbums(AlbumQuery.AlbumGroupKey(album.groupKey))
                .collect { albums ->
                    albums.firstOrNull()?.let { album ->
                        view.setAlbum(album)
                    }
                }
        }
    }

    override fun loadData() {
        launch {
            songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey))
                .filterNotNull()
                .collect { songs ->
                    this@AlbumDetailPresenter.songs = songs
                    view?.setData(songs, getCurrentSong())
                }
        }
    }

    override fun onSongClicked(song: Song) {
        launch {
            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        getCurrentSong()?.let { currentSong ->
            view?.setData(this@AlbumDetailPresenter.songs, currentSong)
        }
    }

    override fun shuffle() {
        launch {
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun addToQueue(album: com.simplecityapps.shuttle.model.Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album.name ?: context.getString(R.string.unknown))
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(R.string.unknown))
        }
    }

    override fun playNext(album: com.simplecityapps.shuttle.model.Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album.name ?: context.getString(R.string.unknown))
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(R.string.unknown))
        }
    }

    override fun getCurrentSong(): Song? {
        return queueManager.getCurrentItem()?.song
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun editTags(album: com.simplecityapps.shuttle.model.Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
    }
}
