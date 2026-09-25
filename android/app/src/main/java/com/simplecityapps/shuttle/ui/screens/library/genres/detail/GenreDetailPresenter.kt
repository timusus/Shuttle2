package com.simplecityapps.shuttle.ui.screens.library.genres.detail

import android.content.Context
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    private val albumsRepository: AlbumRepository,
    private val resolveSongs: ResolveSongs,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
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
        launch { play(songs, songs.indexOf(song)) }
    }

    override fun shuffle() {
        launch {
            val result = shuffleSongs(songs)
            if (result is ShuffleSongs.Result.Failure) view?.showLoadError(Error(result.message))
        }
    }

    override fun addToQueue(genre: Genre) {
        enqueue(MediaSelection.Genres(genre), EnqueueSongs.Position.End) { view?.onAddedToQueue(genre.name) }
    }

    override fun addToQueue(song: Song) {
        enqueue(MediaSelection.Songs(song), EnqueueSongs.Position.End) { view?.onAddedToQueue(song.displayName) }
    }

    override fun playNext(genre: Genre) {
        enqueue(MediaSelection.Genres(genre), EnqueueSongs.Position.Next) { view?.onAddedToQueue(genre.name) }
    }

    override fun playNext(song: Song) {
        enqueue(MediaSelection.Songs(song), EnqueueSongs.Position.Next) { view?.onAddedToQueue(song.displayName) }
    }

    override fun exclude(song: Song) {
        launch { excludeSongs(MediaSelection.Songs(song)) }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun editTags(genre: Genre) {
        launch { view?.showTagEditor(resolveSongs(MediaSelection.Genres(genre))) }
    }

    override fun delete(song: Song) {
        launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) {
                view?.showDeleteError(UserFriendlyError(context.getString(R.string.delete_song_failed)))
            }
        }
    }

    override fun addToQueue(album: Album) {
        enqueue(MediaSelection.Albums(album), EnqueueSongs.Position.End) { view?.onAddedToQueue(album) }
    }

    override fun playNext(album: Album) {
        enqueue(MediaSelection.Albums(album), EnqueueSongs.Position.Next) { view?.onAddedToQueue(album) }
    }

    override fun exclude(album: Album) {
        launch { excludeSongs(MediaSelection.Albums(album)) }
    }

    override fun editTags(album: Album) {
        launch { view?.showTagEditor(resolveSongs(MediaSelection.Albums(album))) }
    }

    override fun play(album: Album) {
        launch { play(resolveSongs(MediaSelection.Albums(album))) }
    }

    private suspend fun play(songs: List<Song>, position: Int = 0) {
        if (songs.isEmpty()) return
        val result = playSongs(songs, position.coerceAtLeast(0))
        if (result is PlaySongs.Result.Failure && result.message != null) view?.showLoadError(Error(result.message))
    }

    private fun enqueue(selection: MediaSelection, position: EnqueueSongs.Position, onAdded: () -> Unit) {
        launch {
            enqueueSongs(selection, position)
            onAdded()
        }
    }

    private val Song.displayName: String
        get() = name ?: context.getString(com.simplecityapps.core.R.string.unknown)
}
