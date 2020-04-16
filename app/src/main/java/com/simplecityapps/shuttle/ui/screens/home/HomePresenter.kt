package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
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
    }
}

class HomePresenter @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val playbackManager: PlaybackManager

) : HomeContract.Presenter, BasePresenter<HomeContract.View>() {

    override fun shuffleAll() {
        addDisposable(songRepository.getSongs()
            .first(emptyList())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { songs ->
                    if (songs.isEmpty()) {
                        view?.showLoadError(UserFriendlyError("Your library is empty"))
                        return@subscribeBy
                    }
                    playbackManager.shuffle(songs) { result ->
                        result.onSuccess {
                            playbackManager.play()
                        }
                        result.onFailure { error -> view?.showLoadError(Error(error)) }
                    }
                },
                onError = { throwable -> Timber.e(throwable, "Error retrieving songs") }
            ))
    }

    override fun loadData() {
        addDisposable(Observables.combineLatest(
                albumRepository.getAlbums(AlbumQuery.PlayCount(1, AlbumSortOrder.PlayCount)).map { albums -> albums.take(20) },
                songRepository.getSongs(SmartPlaylist.RecentlyPlayed.songQuery)
                    .map { songs -> SmartPlaylist.RecentlyPlayed.songQuery?.sortOrder?.let { songSortOrder -> songs.sortedWith(songSortOrder.comparator) } ?: songs }
                    .map { songs -> songs.distinctBy { it.albumId }.map { it.albumId } }
                    .concatMap { albumIds -> albumRepository.getAlbums(AlbumQuery.AlbumIds(albumIds)).take(20) },
                albumRepository.getAlbums(AlbumQuery.Year(Calendar.getInstance().get(Calendar.YEAR))).map { albums -> albums.take(20) },
                albumArtistRepository.getAlbumArtists(AlbumArtistQuery.PlayCount(0, AlbumArtistSortOrder.PlayCount))
                    .map { albumArtists -> albumArtists.shuffled().take(20) }
            ) { mostPlayedAlbums, recentlyPlayedAlbums, albumsFromThisYear, unplayedAlbumArtists ->
                HomeContract.HomeData(
                    mostPlayedAlbums,
                    recentlyPlayedAlbums,
                    albumsFromThisYear,
                    unplayedAlbumArtists
                )
            }
            .subscribeBy(
                onNext = { homeData -> view?.setData(homeData) },
                onError = { throwable -> Timber.e(throwable, "Failed to load home data") }
            ))
    }

    override fun addToQueue(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.addToQueue(songs)
                        view?.onAddedToQueue(albumArtist)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
        )
    }

    override fun playNext(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.playNext(songs)
                        view?.onAddedToQueue(albumArtist)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
        )
    }

    override fun addToQueue(album: Album) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumIds(listOf(album.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.addToQueue(songs)
                        view?.onAddedToQueue(album)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album: ${album.name}") })
        )
    }

    override fun playNext(album: Album) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumIds(listOf(album.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.playNext(songs)
                        view?.onAddedToQueue(album)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album: ${album.name}") })
        )
    }

    override fun blacklist(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .flatMapCompletable { songs ->
                    songRepository.setBlacklisted(songs, true)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
        )
    }

    override fun blacklist(album: Album) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumIds(listOf(album.id)))
                .first(emptyList())
                .flatMapCompletable { songs ->
                    songRepository.setBlacklisted(songs, true)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onError = { throwable -> Timber.e(throwable, "Failed to blacklist album ${album.name}") })
        )
    }
}