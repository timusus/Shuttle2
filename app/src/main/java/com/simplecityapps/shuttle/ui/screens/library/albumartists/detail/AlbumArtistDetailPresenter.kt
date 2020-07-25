package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AlbumArtistDetailContract {

    interface View {
        fun setListData(albums: Map<Album, List<Song>>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(name: String)
        fun setAlbumArtist(albumArtist: AlbumArtist)
        fun showDeleteError(error: Error)
        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song, songs: List<Song>)
        fun shuffle()
        fun addToQueue(albumArtist: AlbumArtist)
        fun play(album: Album)
        fun addToQueue(album: Album)
        fun addToQueue(song: Song)
        fun playNext(album: AlbumArtist)
        fun playNext(album: Album)
        fun playNext(song: Song)
        fun exclude(song: Song)
        fun editTags(song: Song)
        fun exclude(album: Album)
        fun editTags(album: Album)
        fun editTags(albumArtist: AlbumArtist)
        fun delete(song: Song)
    }
}

class AlbumArtistDetailPresenter @AssistedInject constructor(
    private val context: Context,
    private val albumArtistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    @Assisted private val albumArtist: AlbumArtist
) : BasePresenter<AlbumArtistDetailContract.View>(),
    AlbumArtistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(albumArtist: AlbumArtist): AlbumArtistDetailPresenter
    }

    override fun bindView(view: AlbumArtistDetailContract.View) {
        super.bindView(view)

        view.setAlbumArtist(albumArtist)

        launch {
            albumArtistRepository
                .getAlbumArtists(AlbumArtistQuery.AlbumArtist(name = albumArtist.name))
                .collect { albumArtists ->
                    albumArtists.firstOrNull()?.let { albumArtist ->
                        this@AlbumArtistDetailPresenter.view?.setAlbumArtist(albumArtist)
                    }
                }
        }
    }

    override fun loadData() {
        launch {
            albumRepository.getAlbums(AlbumQuery.AlbumArtist(albumArtist.name))
                .combine(songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name))))) { albums, songs ->
                    albums.map { album -> Pair(album, songs.filter { song -> song.album == album.name }) }
                        .sortedWith(Comparator { a, b -> b.first.year.compareTo(a.first.year) })
                        .toMap()
                }
                .collect { map ->
                    view?.setListData(map)
                }
        }
    }

    override fun onSongClicked(song: Song, songs: List<Song>) {
        launch {
            playbackManager.load(songs, songs.indexOf(song)) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun shuffle() {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.shuffle(songs) { result ->
                result.onSuccess {
                    playbackManager.play()
                }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtist.name)
        }
    }

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album.name)
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song.name)
        }
    }

    override fun playNext(album: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist.name)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album.name)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song.name)
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }
    
    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.removeSong(song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError("The song couldn't be deleted"))
        }
    }

    override fun play(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            playbackManager.load(songs, 0) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }
}