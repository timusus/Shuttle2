package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class AlbumArtistDetailContract {

    interface View {
        fun setListData(albums: Map<Album, List<Song>>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(name: String)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song, songs: List<Song>)
        fun shuffle()
        fun addToQueue(albumArtist: AlbumArtist)
        fun addToQueue(album: Album)
        fun addToQueue(song: Song)
        fun playNext(album: AlbumArtist)
        fun playNext(album: Album)
        fun playNext(song: Song)
    }
}

class AlbumArtistDetailPresenter @AssistedInject constructor(
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

    override fun loadData() {
        val songsSingle = songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id))).first(emptyList())
        val albumsSingle = albumRepository.getAlbums(AlbumQuery.AlbumArtistId(albumArtist.id))
            .first(emptyList())

        addDisposable(
            Single.zip(albumsSingle, songsSingle, BiFunction<List<Album>, List<Song>, Map<Album, List<Song>>> { albums, songs ->
                albums.map { album -> Pair(album, songs.filter { song -> song.albumId == album.id }) }
                    .sortedWith(Comparator { a, b -> b.first.year.compareTo(a.first.year) })
                    .toMap()
            })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { map ->
                    view?.setListData(map)
                })
    }

    override fun onSongClicked(song: Song, songs: List<Song>) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }

    override fun shuffle() {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id))).first(emptyList())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.shuffle(songs) { result ->
                            result.onSuccess {
                                playbackManager.play()
                            }
                            result.onFailure { error -> view?.showLoadError(error as Error) }
                        }
                    }, onError = { throwable ->
                        Timber.e(throwable, "Failed to retrieve songs")
                    })
        )
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
                        view?.onAddedToQueue(albumArtist.name)
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
                        view?.onAddedToQueue(album.name)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album: ${album.name}") })
        )
    }

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song.name)
    }

    override fun playNext(album: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.playNext(songs)
                        view?.onAddedToQueue(albumArtist.name)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album artist: ${albumArtist.name}") })
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
                        view?.onAddedToQueue(album.name)
                    },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve songs for album: ${album.name}") })
        )
    }

    override fun playNext(song: Song) {
        playbackManager.playNext(listOf(song))
        view?.onAddedToQueue(song.name)
    }
}