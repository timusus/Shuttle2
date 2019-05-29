package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import kotlin.random.Random

class AlbumArtistDetailPresenter @AssistedInject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val albumArtist: AlbumArtist
) : BasePresenter<AlbumArtistDetailContract.View>(),
    AlbumArtistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(albumArtist: AlbumArtist): AlbumArtistDetailPresenter
    }

    override fun loadData() {
        val songsSingle = songRepository.getSongs(SongQuery.AlbumArtistId(albumArtist.id)).first(emptyList())
        val albumsSingle = albumRepository.getAlbums(AlbumQuery.AlbumArtistId(albumArtist.id))
            .first(emptyList())

        addDisposable(Single.zip(albumsSingle, songsSingle, BiFunction<List<Album>, List<Song>, Map<Album, List<Song>>> { albums, songs ->
            val map = hashMapOf<Album, List<Song>>()
            albums.forEach { album ->
                map[album] = songs.filter { song -> song.albumId == album.id }
            }
            map
        })
            .map { map ->
                map.toSortedMap(Comparator { a, b -> a.year.compareTo(b.year) })
                // Fixme:  For some reason, we can't return the above statement directly, or albums disappear from our map. Figure out why.
                map
            }
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
            songRepository.getSongs(SongQuery.AlbumArtistId(albumArtist.id)).first(emptyList())
            .subscribeBy(
                onSuccess = { songs ->
                    playbackManager.load(songs, Random.nextInt(songs.size)) { result ->
                        result.onSuccess {
                            queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
                            playbackManager.play()
                        }
                        result.onFailure { error -> view?.showLoadError(error as Error) }
                    }
                }, onError = { throwable ->
                    Timber.e(throwable, "Failed to retrieve songs")
                })
        )
    }
}