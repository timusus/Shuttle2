package com.simplecityapps.shuttle.ui.screens.playlistmenu

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
        launch {
            playlistRepository.getPlaylists().collect { playlists ->
                this@PlaylistMenuPresenter.playlists = playlists
            }
        }
    }

    override fun createPlaylist(name: String, playlistData: PlaylistData?) {
        launch {
            val songs = playlistData?.getSongs()
            val playlist = playlistRepository.createPlaylist(name, null, songs)

            if (playlistData != null) {
                view?.onAddedToPlaylist(playlist, playlistData)
            } else {
                view?.onPlaylistCreated(playlist)
            }
        }
    }

    override fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData) {
        launch {
            val songs = playlistData.getSongs()
            try {
                playlistRepository.addToPlaylist(playlist, songs)
                view?.onAddedToPlaylist(playlist, playlistData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add to playlist")
                view?.onPlaylistAddFailed(Error(e))
            }
        }
    }

    private suspend fun PlaylistData.getSongs(): List<Song> {
        return when (this) {
            is PlaylistData.Songs -> return data
            is PlaylistData.Albums -> songRepository.getSongs(SongQuery.Albums(data.map { album -> SongQuery.Album(name = album.name, albumArtistName = album.albumArtist) })).firstOrNull().orEmpty()
            is PlaylistData.AlbumArtists -> songRepository.getSongs(SongQuery.AlbumArtists(data.map { albumArtist -> SongQuery.AlbumArtist(name = albumArtist.name) })).firstOrNull().orEmpty()
        }
    }
}