package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

interface HomeContract {

    data class HomeData(
        val mostPlayedAlbums: List<Album>,
        val recentlyPlayedAlbums: List<Album>,
        val albumsFromThisYear: List<Album>,
        val unplayedAlbumArtists: List<AlbumArtist>
    )

    interface View {
        fun showLoadError(error: Error)
        fun setData(data: HomeData)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun onAddedToQueue(album: Album)
        fun showDeleteError(error: Error)
    }

    interface Presenter {
        fun shuffleAll()
        fun loadData()
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun blacklist(albumArtist: AlbumArtist)
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun blacklist(album: Album)
        fun play(albumArtist: AlbumArtist)
        fun play(album: Album)
    }
}

class HomePresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val playbackManager: PlaybackManager

) : HomeContract.Presenter, BasePresenter<HomeContract.View>() {

    override fun shuffleAll() {
        launch {
            val songs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
            if (songs.isEmpty()) {
                view?.showLoadError(UserFriendlyError("Your library is empty"))
            } else {
                playbackManager.shuffle(songs) { result ->
                    result.onSuccess {
                        playbackManager.play()
                    }
                    result.onFailure { error -> view?.showLoadError(Error(error)) }
                }
            }
        }
    }

    override fun loadData() {
        launch {
            val mostPlayedAlbums = albumRepository.getAlbums(AlbumQuery.PlayCount(2, AlbumSortOrder.PlayCount))
                .map { it.take(20) }

            var songs = songRepository.getSongs(SmartPlaylist.RecentlyPlayed.songQuery).firstOrNull().orEmpty()
            songs = SmartPlaylist.RecentlyPlayed.songQuery?.sortOrder?.let { songSortOrder -> songs.sortedWith(songSortOrder.comparator) } ?: songs
            val recentlyPlayedAlbums = albumRepository.getAlbums(AlbumQuery.Albums(songs.distinctBy { it.album }.map { AlbumQuery.Album(name = it.album, albumArtistName = it.albumArtist) }))
                .map { it.take(20) }

            val albumsFromThisYear = albumRepository.getAlbums(AlbumQuery.Year(Calendar.getInstance().get(Calendar.YEAR)))
                .map { it.take(20) }

            val unplayedAlbumArtists = albumArtistRepository.getAlbumArtists(AlbumArtistQuery.PlayCount(0, AlbumArtistSortOrder.PlayCount))
                .map { it.shuffled() }
                .map { it.take(20) }

            combine(mostPlayedAlbums, recentlyPlayedAlbums, albumsFromThisYear, unplayedAlbumArtists) { mostPlayedAlbums, recentlyPlayedAlbums, albumsFromThisYear, unplayedAlbumArtists ->
                HomeContract.HomeData(
                    mostPlayedAlbums,
                    recentlyPlayedAlbums,
                    albumsFromThisYear,
                    unplayedAlbumArtists
                )
            }.collect { homeData ->
                view?.setData(homeData)
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

    override fun blacklist(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.AlbumArtists(listOf(SongQuery.AlbumArtist(name = albumArtist.name)))).firstOrNull().orEmpty()
            songRepository.setBlacklisted(songs, true)
        }
    }

    override fun blacklist(album: Album) {
        launch {
            val songs = songRepository.getSongs(SongQuery.Albums(listOf(SongQuery.Album(name = album.name, albumArtistName = album.albumArtist)))).firstOrNull().orEmpty()
            songRepository.setBlacklisted(songs, true)
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
}