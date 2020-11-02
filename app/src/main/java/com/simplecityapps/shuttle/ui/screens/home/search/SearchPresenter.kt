package com.simplecityapps.shuttle.ui.screens.home.search

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SearchContract : BaseContract.Presenter<SearchContract.View> {

    interface View {
        fun setData(searchResult: Triple<List<AlbumArtist>, List<Album>, List<Song>>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun onAddedToQueue(album: Album)
        fun onAddedToQueue(song: Song)
        fun showDeleteError(error: Error)
        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter {
        fun loadData(query: String)
        fun play(albumArtist: AlbumArtist)
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun exclude(albumArtist: AlbumArtist)
        fun editTags(albumArtist: AlbumArtist)
        fun play(album: Album)
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun exclude(album: Album)
        fun editTags(album: Album)
        fun play(song: Song)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun exclude(song: Song)
        fun delete(song: Song)
        fun editTags(song: Song)
    }
}

class SearchPresenter @Inject constructor(
    private val context: Context,
    private val songRepository: SongRepository,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val playbackManager: PlaybackManager
) :
    BasePresenter<SearchContract.View>(),
    SearchContract.Presenter {

    private var searchResult: Triple<List<AlbumArtist>, List<Album>, List<Song>> = Triple(emptyList(), emptyList(), emptyList())

    private var queryJob: Job? = null

    override fun loadData(query: String) {
        queryJob?.cancel()
        queryJob = launch {
            val albumArtists = artistRepository.getAlbumArtists(AlbumArtistQuery.Search(query))
                .map { albumArtists ->
                    albumArtists.sortedBy { it.name }
                }

            val albums = albumRepository.getAlbums(AlbumQuery.Search(query))
                .map { albums ->
                    albums
                        .asSequence()
                        .sortedBy { it.name }
                        .sortedByDescending { it.name.contains(query, true) }
                        .sortedByDescending { it.albumArtist.contains(query, true) }
                        .toList()
                }

            val songs = songRepository.getSongs(SongQuery.Search(query))
                .map { songs ->
                    songs
                        .asSequence()
                        .sortedBy { it.track }
                        .sortedBy { it.albumArtist }
                        .sortedBy { it.album }
                        .sortedByDescending { it.year }
                        .sortedByDescending { it.name.contains(query, true) }
                        .sortedByDescending { it.albumArtist.contains(query, true) }
                        .sortedByDescending { it.album.contains(query, true) }
                        .toList()
                }

            combine(albumArtists, albums, songs) { artists, albums, songs ->
                Triple(artists, albums, songs)
            }
                .flowOn(Dispatchers.IO)
                .collect { results ->
                    searchResult = results
                    view?.setData(results)
                }
        }
    }

    override fun play(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.load(songs, 0) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
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

    override fun play(song: Song) {
        launch {
            playbackManager.load(searchResult.third, searchResult.third.indexOf(song)) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(error as Error) }
            }
        }
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun playNext(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun addToQueue(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun playNext(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(album)
        }
    }

    override fun addToQueue(song: Song) {
        launch {
            playbackManager.addToQueue(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun playNext(song: Song) {
        launch {
            playbackManager.playNext(listOf(song))
            view?.onAddedToQueue(song)
        }
    }

    override fun exclude(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun exclude(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun exclude(song: Song) {
        launch {
            songRepository.setExcluded(listOf(song), true)
        }
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
        }
    }

    override fun editTags(song: Song) {
        view?.showTagEditor(listOf(song))
    }

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            launch {
                songRepository.remove(song)
            }
        } else {
            view?.showDeleteError(UserFriendlyError("The song couldn't be deleted"))
        }
    }
}