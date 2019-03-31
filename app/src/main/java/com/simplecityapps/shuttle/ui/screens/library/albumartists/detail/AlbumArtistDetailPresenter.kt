package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import kotlin.random.Random

class AlbumArtistDetailPresenter @AssistedInject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val albumArtistId: Long
) : BasePresenter<AlbumArtistDetailContract.View>(),
    AlbumArtistDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(albumArtistId: Long): AlbumArtistDetailPresenter
    }

    override fun bindView(view: AlbumArtistDetailContract.View) {
        super.bindView(view)

        addDisposable(albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistId(albumArtistId)).firstOrError()
            .map { it.firstOrNull() }
            .subscribe(
                { albumArtist ->
                    albumArtist?.let {
                        view.setCurrentAlbumArtist(albumArtist)
                    }
                },
                { error -> Timber.e(error, "Failed to retrieve name for album artist $albumArtistId") })
        )
    }

    override fun loadData() {
        val songsSingle = songRepository.getSongs(SongQuery.AlbumArtistId(albumArtistId)).first(emptyList())
        val albumsSingle = albumRepository.getAlbums(AlbumQuery.AlbumArtistId(albumArtistId))
            .first(emptyList())

        addDisposable(Single.zip(albumsSingle, songsSingle, BiFunction<List<Album>, List<Song>, Map<Album, List<Song>>> { albums, songs ->
            val map = hashMapOf<Album, List<Song>>()
            albums.forEach { album ->
                map[album] = songs.filter { it.albumId == album.id }
            }
            map
        })
            .map { map ->
                map.toSortedMap(Comparator { a, b -> a.year.compareTo(b.year) })
                // Fixme:  For some reason, we can't return the above statement directly, or albums disappear from our map. Figure out why.
                map
            }
            .subscribe { map ->
                view?.setListData(map)
            })
    }

    override fun onSongClicked(song: Song, songs: List<Song>) {
        playbackManager.load(song, songs, 0, true)
    }

    override fun shuffle() {
        addDisposable(songRepository.getSongs(SongQuery.AlbumArtistId(albumArtistId)).first(emptyList())
            .subscribeBy(
                onSuccess = { songs ->
                    queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
                    playbackManager.load(songs, Random.nextInt(songs.size), 0, true)
                }, onError = { throwable ->
                    Timber.e(throwable, "Failed to retrieve songs")
                })
        )
    }
}