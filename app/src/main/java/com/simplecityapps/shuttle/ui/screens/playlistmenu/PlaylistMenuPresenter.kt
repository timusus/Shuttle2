package com.simplecityapps.shuttle.ui.screens.playlistmenu

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class PlaylistMenuPresenter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) : PlaylistMenuContract.Presenter,
    BasePresenter<PlaylistMenuContract.View>() {

    override var playlists: List<Playlist> = emptyList()

    override fun bindView(view: PlaylistMenuContract.View) {
        super.bindView(view)

        loadPlaylists()
    }

    override fun loadPlaylists() {
        addDisposable(
            playlistRepository.getPlaylists()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { playlists -> this.playlists = playlists },
                    onError = { error -> Timber.e(error, "Failed to load playlists") })
        )
    }

    override fun createPlaylist(name: String, playlistData: PlaylistData?) {
        addDisposable((playlistData?.getSongs() ?: Single.just(emptyList())).flatMap { songs ->
            playlistRepository.createPlaylist(name, songs)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { playlist ->
                    if (playlistData != null) {
                        view?.onAddedToPlaylist(playlist, playlistData)
                    } else {
                        view?.onPlaylistCreated(playlist)
                    }
                },
                onError = { error ->
                    Timber.e(error, "Failed to create playlist")
                }
            ))
    }

    override fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData) {
        addDisposable(playlistData.getSongs().flatMapCompletable { songs ->
            playlistRepository.addToPlaylist(playlist, songs)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onComplete = { view?.onAddedToPlaylist(playlist, playlistData) },
                onError = { throwable ->
                    Timber.e(throwable, "Failed to add song to playlist")
                    view?.onPlaylistAddFailed(Error(throwable))
                }
            ))
    }

    private fun PlaylistData.getSongs(): Single<List<Song>> {
        return when (this) {
            is PlaylistData.Songs -> return Single.just(data)
            is PlaylistData.Albums -> songRepository.getSongs(SongQuery.AlbumIds(data.map { album -> album.id })).first(emptyList())
            is PlaylistData.AlbumArtists -> songRepository.getSongs(SongQuery.AlbumArtistIds(data.map { albumArtist -> albumArtist.id })).first(emptyList())
        }
    }
}