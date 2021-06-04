package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Named

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
        fun showTagEditor(songs: List<Song>)
    }

    interface Presenter {
        fun shuffleAll()
        fun loadData()
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun exclude(albumArtist: AlbumArtist)
        fun editTags(albumArtist: AlbumArtist)
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun exclude(album: Album)
        fun editTags(album: Album)
        fun play(albumArtist: AlbumArtist)
        fun play(album: Album)
    }
}

class HomePresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Named("randomSeed") private val seed: Long
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
            val mostPlayedAlbums = albumRepository
                .getAlbums(
                    AlbumQuery.PlayCount(
                        count = 2,
                        sortOrder = AlbumSortOrder.PlayCount
                    )
                )
                .map { it.take(20) }

            val recentlyPlayedAlbums = albumRepository
                .getAlbums(AlbumQuery.All(sortOrder = AlbumSortOrder.RecentlyPlayed))
                .map { it.take(20) }

            val albumsFromThisYear = albumRepository
                .getAlbums(AlbumQuery.Year(Calendar.getInstance().get(Calendar.YEAR)))
                .map { it.take(20) }

            val unplayedAlbumArtists = albumArtistRepository
                .getAlbumArtists(AlbumArtistQuery.PlayCount(0))
                .map { it.shuffled(Random(seed)) }
                .map { it.take(20) }

            combine(mostPlayedAlbums, recentlyPlayedAlbums, albumsFromThisYear, unplayedAlbumArtists) { mostPlayedAlbums, recentlyPlayedAlbums, albumsFromThisYear, unplayedAlbumArtists ->
                HomeContract.HomeData(
                    mostPlayedAlbums = mostPlayedAlbums,
                    recentlyPlayedAlbums = recentlyPlayedAlbums,
                    albumsFromThisYear = albumsFromThisYear,
                    unplayedAlbumArtists = unplayedAlbumArtists
                )
            }.collect { homeData ->
                view?.setData(homeData)
            }
        }
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            view?.onAddedToQueue(albumArtist)
        }
    }

    override fun playNext(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            view?.onAddedToQueue(albumArtist)
        }
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

    override fun exclude(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    override fun editTags(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            view?.showTagEditor(songs)
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

    override fun play(albumArtist: AlbumArtist) {
        launch {
            val songs = songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey)))).firstOrNull().orEmpty()
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error -> view?.showLoadError(error as Error) }
                }
            }
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