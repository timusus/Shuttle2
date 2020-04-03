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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface SearchContract : BaseContract.Presenter<SearchContract.View> {

    interface Presenter {
        fun loadData(query: String)
        fun onSongClicked(song: Song)
        fun onAlbumArtistCLicked(albumArtist: AlbumArtist)
        fun onAlbumClicked(album: Album)
        fun addToQueue(albumArtist: AlbumArtist)
        fun playNext(albumArtist: AlbumArtist)
        fun blacklist(albumArtist: AlbumArtist)
        fun addToQueue(album: Album)
        fun playNext(album: Album)
        fun blacklist(album: Album)
        fun addToQueue(song: Song)
        fun playNext(song: Song)
        fun blacklist(song: Song)
        fun delete(song: Song)
    }

    interface View {
        fun setData(searchResult: Triple<List<AlbumArtist>, List<Album>, List<Song>>)
        fun showLoadError(error: Error)
        fun onAddedToQueue(albumArtist: AlbumArtist)
        fun onAddedToQueue(album: Album)
        fun onAddedToQueue(song: Song)
        fun showDeleteError(error: Error)
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

    private var queryDisposable: Disposable? = null

    override fun loadData(query: String) {
        queryDisposable?.dispose()

        queryDisposable = Observables.combineLatest(
            artistRepository.getAlbumArtists(AlbumArtistQuery.Search(query))
                .map { albumArtists ->
                    albumArtists.sortedBy { it.name }
                },
            albumRepository.getAlbums(AlbumQuery.Search(query))
                .map { albums ->
                    albums
                        .asSequence()
                        .sortedBy { it.name }
                        .sortedByDescending { it.name.contains(query, true) }
                        .sortedByDescending { it.albumArtistName.contains(query, true) }
                        .toList()
                },
            songRepository.getSongs(SongQuery.Search(query))
                .map { songs ->
                    songs
                        .asSequence()
                        .sortedBy { it.track }
                        .sortedBy { it.albumArtistName }
                        .sortedBy { it.albumName }
                        .sortedByDescending { it.year }
                        .sortedByDescending { it.name.contains(query, true) }
                        .sortedByDescending { it.albumArtistName.contains(query, true) }
                        .sortedByDescending { it.albumName.contains(query, true) }
                        .toList()
                }
        ) { albumArtists, albums, songs -> Triple(albumArtists, albums, songs) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { searchResults ->
                    this.searchResult = searchResults
                    view?.setData(searchResults)
                },
                onError = { error -> Timber.e(error, "Failed to load songs") }
            )
        addDisposable(queryDisposable!!)
    }

    override fun onAlbumArtistCLicked(albumArtist: AlbumArtist) {
        addDisposable(
            songRepository.getSongs(SongQuery.AlbumArtistIds(listOf(albumArtist.id)))
                .first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onSuccess = { songs ->
                    playbackManager.load(songs, 0) { result ->
                        result.onSuccess { playbackManager.play() }
                        result.onFailure { error -> view?.showLoadError(error as Error) }
                    }
                }, onError = { error ->
                    Timber.e(error, "Failed to retrieve songs for album artist")
                })
        )
    }

    override fun onAlbumClicked(album: Album) {
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

    override fun onSongClicked(song: Song) {
        playbackManager.load(searchResult.third, searchResult.third.indexOf(song)) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
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

    override fun addToQueue(song: Song) {
        playbackManager.addToQueue(listOf(song))
        view?.onAddedToQueue(song)
    }

    override fun playNext(song: Song) {
        playbackManager.playNext(listOf(song))
        view?.onAddedToQueue(song)
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

    override fun blacklist(song: Song) {
        addDisposable(
            songRepository.setBlacklisted(listOf(song), true)
                .subscribeOn(Schedulers.io())
                .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to blacklist song") })
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
}