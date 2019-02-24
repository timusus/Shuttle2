package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumQuery
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.queue.PlaybackManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import timber.log.Timber

class AlbumDetailPresenter @AssistedInject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val playbackManager: PlaybackManager,
    @Assisted private val albumId: Long
) : BasePresenter<AlbumDetailContract.View>(), AlbumDetailContract.Presenter {

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
                        view.setTitle(album.name, album.albumArtistName)
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

    fun onSongClicked(song: Song){
        playbackManager.play(songs, songs.indexOf(song))
    }
}