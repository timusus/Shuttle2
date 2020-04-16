package com.simplecityapps.shuttle.ui.screens.playback

import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class PlaybackPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository
) : BasePresenter<PlaybackContract.View>(),
    PlaybackContract.Presenter,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var isFavoriteDisposable: Disposable? = null

    override fun bindView(view: PlaybackContract.View) {
        super.bindView(view)

        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        // One time update of all UI components
        updateProgress()
        onQueueChanged()
        onQueuePositionChanged(null, queueManager.getCurrentPosition())
        onPlaystateChanged(playbackManager.isPlaying())
        onShuffleChanged()
        onRepeatChanged()

        addDisposable(playlistRepository.getPlaylists(PlaylistQuery.PlaylistName("Favorites"))
            .first(emptyList())
            .flatMapObservable { playlists ->
                playlists.firstOrNull()?.let { favoritesPlaylist ->
                    playlistRepository.getSongsForPlaylist(favoritesPlaylist.id)
                } ?: Observable.just(emptyList())
            }
            .map { it.contains(queueManager.getCurrentItem()?.song) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { isFavorite -> view?.setIsFavorite(isFavorite) },
                onError = { error -> Timber.e(error, "Failed to ") }
            )
        )

    }

    override fun unbindView() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)

        super.unbindView()
    }


    // Private

    private fun updateProgress() {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            onProgressChanged(playbackManager.getProgress() ?: 0, playbackManager.getDuration() ?: currentSong.duration)
        }
    }


    // PlaybackContract.Presenter Implementation

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun toggleShuffle() {
        queueManager.toggleShuffleMode()
    }

    override fun toggleRepeat() {
        queueManager.toggleRepeatMode()
    }

    override fun skipNext() {
        playbackManager.skipToNext(true)
    }

    override fun skipPrev() {
        playbackManager.skipToPrev()
    }

    override fun skipTo(position: Int) {
        playbackManager.skipTo(position)
    }

    override fun seekForward(seconds: Int) {
        Timber.v("seekForward() seconds: $seconds")
        playbackManager.getProgress()?.let { position ->
            playbackManager.seekTo(position + seconds * 1000)
        }
    }

    override fun seekBackward(seconds: Int) {
        Timber.v("seekBackward() seconds: $seconds")
        playbackManager.getProgress()?.let { position ->
            playbackManager.seekTo(position - seconds * 1000)
        }
    }

    override fun seek(fraction: Float) {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            playbackManager.seekTo(((playbackManager.getDuration() ?: currentSong.duration) * fraction).toInt())
        } ?: Timber.v("seek() failed, current song null")
    }

    override fun sleepTimerClicked() {
        view?.presentSleepTimer()
    }

    override fun setFavorite(isFavorite: Boolean) {
        queueManager.getCurrentItem()?.song?.let { song ->
            addDisposable(
                playlistRepository.getPlaylists(PlaylistQuery.PlaylistName("Favorites"))
                    .first(emptyList())
                    .flatMapCompletable { playlists ->
                        playlists.firstOrNull()?.let { playlist ->
                            if (isFavorite) {
                                playlistRepository.addToPlaylist(playlist, listOf(song))
                            } else {
                                playlistRepository.removeFromPlaylist(playlist, listOf(song))
                            }
                        } ?: Completable.error(Error("Favorites playlist not found"))
                    }
                    .subscribeOn(Schedulers.io())
                    .subscribeBy(onError = { error ->
                        Timber.e(error, "Failed to add to favorites")
                    })
            )
        }
    }

    override fun goToAlbum() {
        queueManager.getCurrentItem()?.song?.let { song ->
            albumRepository.getAlbums(AlbumQuery.AlbumIds(listOf(song.albumId))).first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { albums ->
                        albums.firstOrNull()?.let { album ->
                            view?.goToAlbum(album)
                        } ?: Timber.e("Failed to retrieve album for song: ${song.name}")
                    },
                    onError = {
                        Timber.e(it, "Failed to retrieve album for song: ${song.name}")
                    }
                )
        }

    }

    override fun goToArtist() {
        queueManager.getCurrentItem()?.song?.let { song ->
            albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistId(song.albumArtistId)).first(emptyList())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { artists ->
                        artists.firstOrNull()?.let { artist ->
                            view?.goToArtist(artist)
                        } ?: Timber.e("Failed to retrieve album artist for song: ${song.name}")
                    },
                    onError = {
                        Timber.e(it, "Failed to retrieve album artist for song: ${song.name}")
                    }
                )
        }
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        view?.setPlayState(isPlaying)
    }


    // PlaybackManager.ProgressCallback

    override fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
        view?.setProgress(position, duration)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.setQueue(queueManager.getQueue(), queueManager.getCurrentPosition())
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        view?.setCurrentSong(queueManager.getCurrentItem()?.song)
        view?.setQueuePosition(queueManager.getCurrentPosition(), queueManager.getSize(), abs((newPosition ?: 0) - (oldPosition ?: 0)) <= 1)

        isFavoriteDisposable?.dispose()
        queueManager.getCurrentItem()?.song?.let { song ->
            isFavoriteDisposable = playlistRepository
                .getPlaylists(PlaylistQuery.PlaylistName("Favorites"))
                .first(emptyList()).flatMap { playlists ->
                    playlists.firstOrNull()?.let { playlist ->
                        playlistRepository.getSongsForPlaylist(playlist.id).first(emptyList()).flatMap { songs ->
                            Single.just(songs.contains(song))
                        }
                    } ?: Single.just(false)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { isFavorite -> view?.setIsFavorite(isFavorite) },
                    onError = { error -> Timber.e(error, "Failed to determine if song is favorite") }
                )
        }
    }

    override fun onShuffleChanged() {
        view?.setShuffleMode(queueManager.getShuffleMode())
    }

    override fun onRepeatChanged() {
        view?.setRepeatMode(queueManager.getRepeatMode())
    }
}