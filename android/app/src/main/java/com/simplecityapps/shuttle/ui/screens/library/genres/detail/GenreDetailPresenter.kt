package com.simplecityapps.shuttle.ui.screens.library.genres.detail

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

interface GenreDetailContract {
    interface View {
        fun setData(
            albums: List<Album>,
            songs: List<Song>
        )

        fun showLoadError(error: Error)

        fun onAddedToQueue(name: String)

        fun onAddedToQueue(album: Album)

        fun setGenre(genre: Genre)

        fun showDeleteError(error: Error)

        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()

        fun onSongClicked(song: Song)

        fun shuffle()

        fun addToQueue(genre: Genre)

        fun addToQueue(song: Song)

        fun playNext(genre: Genre)

        fun playNext(song: Song)

        fun exclude(song: Song)

        fun editTags(song: Song)

        fun editTags(genre: Genre)

        fun delete(song: Song)

        fun addToQueue(album: Album)

        fun playNext(album: Album)

        fun exclude(album: Album)

        fun editTags(album: Album)

        fun play(album: Album)
    }
}

class GenreDetailPresenter
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val albumsRepository: AlbumRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val genre: Genre
) : BasePresenter<GenreDetailContract.View>(),
    GenreDetailContract.Presenter {
    @AssistedFactory
    interface Factory {
        fun create(genre: Genre): GenreDetailPresenter
    }

    private var songs: List<Song> = emptyList()
    private var albums: List<Album> = emptyList()

    override fun bindView(view: GenreDetailContract.View) {
        super.bindView(view)

        view.setGenre(genre)

        launch {
            genreRepository.getGenres(GenreQuery.GenreName(genre.name))
                .collect { genres ->
                    genres.firstOrNull()?.let { genre ->
                        view.setGenre(genre)
                    }
                }
        }
    }

    override fun loadData() {
        launch {
            genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                .collect { songs ->
                    val albums = albumsRepository.getAlbums(AlbumQuery.AlbumGroupKeys(songs.map { AlbumQuery.AlbumGroupKey(it.albumGroupKey) })).first()
                    this@GenreDetailPresenter.albums = albums
                    this@GenreDetailPresenter.songs = songs
                    view?.setData(albums, songs)
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

    override fun shuffle() {
        launch {
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun addToQueue(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All()).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(genre.name)
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun playNext(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All()).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(genre.name)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(queueManager.getQueue().filter { it.song.id == song.id })
        }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun editTags(genre: Genre) {
        launch {
            val songs = genreRepository.getSongsForGenre(genre.name, SongQuery.All()).firstOrNull().orEmpty()
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

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun play(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(key = album.groupKey)))).firstOrNull().orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
        }
    }
}
