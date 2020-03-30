package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.mediaprovider.model.Album
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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

interface AlbumDetailContract {

    interface View {
        fun setData(songs: List<Song>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(name: String)
        fun setAlbum(album: Album)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()
        fun onSongClicked(song: Song)
        fun shuffle()
        fun addToQueue(album: Album)
        fun addToQueue(song: Song)
        fun playNext(album: Album)
        fun playNext(song: Song)
        fun blacklist(song: Song)
    }
}

class AlbumDetailPresenter @AssistedInject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    @Assisted private val album: Album
) : BasePresenter<AlbumDetailContract.View>(),
    AlbumDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(album: Album): AlbumDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun bindView(view: AlbumDetailContract.View) {
        super.bindView(view)

        view.setAlbum(album)
        addDisposable(albumRepository
            .getAlbums(AlbumQuery.AlbumId(album.id))
            .map { it.firstOrNull() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ album ->
                album?.let { view.setAlbum(album) }
            }, { error ->
                Timber.e(error, "Failed to retrieve album")
            })
        )
    }

    override fun loadData() {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumIds(listOf(album.id)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { songs ->
                    this.songs = songs
                    view?.setData(songs)
                })
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(songs, songs.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }

    override fun shuffle() {
        playbackManager.shuffle(songs) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
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
}