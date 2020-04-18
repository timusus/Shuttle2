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
import io.reactivex.Observable
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
        fun setAlbumArtist(albumArtist: AlbumArtist)
        fun showDeleteError(error: Error)
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
        fun blacklist(song: Song)
        fun blacklist(album: Album)
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
        addDisposable(albumArtistRepository
            .getAlbumArtists(AlbumArtistQuery.AlbumArtistId(albumArtist.id))
            .map { it.firstOrNull() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ albumArtist ->
                albumArtist?.let { view.setAlbumArtist(albumArtist) }
            }, { error ->
                Timber.e(error, "Failed to retrieve album artist")
            })
        )
    }

    override fun loadData() {
        val songsObservable = songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
        val albumsObservable = albumRepository.getAlbums(AlbumQuery.AlbumArtistId(albumArtist.id))

        addDisposable(
            Observable.zip(albumsObservable, songsObservable, BiFunction<List<Album>, List<Song>, Map<Album, List<Song>>> { albums, songs ->
                albums.map { album -> Pair(album, songs.filter { song -> song.albumId == album.id }) }
                    .sortedWith(Comparator { a, b -> b.first.year.compareTo(a.first.year) })
                    .toMap()
            })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { map -> view?.setListData(map) },
                    onError = { Timber.e(it, "Failed to load album artist album/song data") })
        )
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

    override fun blacklist(song: Song) {
        addDisposable(
            songRepository.setBlacklisted(listOf(song), true)
                .subscribeOn(Schedulers.io())
                .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to blacklist song") })
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

    override fun delete(song: Song) {
        val uri = song.path.toUri()
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile?.delete() == true) {
            addDisposable(songRepository.removeSong(song)
                .subscribeOn(Schedulers.io())
                .subscribeBy(
                    onComplete = { Timber.i("Song deleted") },
                    onError = { throwable -> Timber.e(throwable, "Failed to remove song from database") }
                ))
        } else {
            view?.showDeleteError(UserFriendlyError("The song couldn't be deleted"))
        }
    }

    override fun play(album: Album) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumIds(listOf(album.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onSuccess = { songs ->
                    playbackManager.load(songs, 0) { result ->
                        result.onSuccess { playbackManager.play() }
                        result.onFailure { error -> view?.showLoadError(error as Error) }
                    }
                }, onError = { error ->
                    Timber.e(error, "Failed to retrieve songs for album")
                })
        )
    }
}