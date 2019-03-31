package com.simplecityapps.shuttle.ui.screens.library.albums.detail

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
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import kotlin.random.Random

class AlbumDetailPresenter @AssistedInject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    @Assisted private val albumId: Long
) : BasePresenter<AlbumDetailContract.View>(),
    AlbumDetailContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(albumId: Long): AlbumDetailPresenter
    }

    private var songs: List<Song> = emptyList()

    override fun bindView(view: AlbumDetailContract.View) {
        super.bindView(view)

        addDisposable(albumRepository.getAlbums(AlbumQuery.AlbumId(albumId)).firstOrError()
            .map { it.firstOrNull() }
            .subscribe(
                { album ->
                    album?.let {
                        view.setCurrentAlbum(album)
                    }
                },
                { error -> Timber.e(error, "Failed to retrieve name for album $albumId") })
        )
    }

    override fun loadData() {
        addDisposable(songRepository.getSongs(SongQuery.AlbumId(albumId)).subscribe { songs ->
            this.songs = songs
            view?.setData(songs)
        })
    }

    override fun onSongClicked(song: Song) {
        playbackManager.load(song, songs, 0, true)
    }

    override fun shuffle() {
        addDisposable(songRepository.getSongs(SongQuery.AlbumId(albumId)).first(emptyList())
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