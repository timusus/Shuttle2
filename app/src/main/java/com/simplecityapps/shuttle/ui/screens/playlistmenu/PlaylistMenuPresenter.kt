package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.Context
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

interface PlaylistMenuContract {

    interface View : CreatePlaylistDialogFragment.Listener {
        fun onPlaylistCreated(playlist: Playlist)
        fun onAddedToPlaylist(playlist: Playlist, playlistData: PlaylistData)
        fun onPlaylistAddFailed(error: Error)
        fun showCreatePlaylistDialog(playlistData: PlaylistData)
        fun onAddToPlaylistWithDuplicates(playlist: Playlist, playlistData: PlaylistData, deduplicatedPlaylistData: PlaylistData.Songs, duplicates: List<Song>)
    }

    interface Presenter : BaseContract.Presenter<View> {
        var playlists: List<Playlist>
        fun loadPlaylists()
        fun createPlaylist(name: String, playlistData: PlaylistData?)
        fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false)
        fun setIgnorePlaylistDuplicates(ignorePlaylistDuplicates: Boolean)
    }
}

class PlaylistMenuPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val queueManager: QueueManager,
    private val preferenceManager: GeneralPreferenceManager
) : PlaylistMenuContract.Presenter,
    BasePresenter<PlaylistMenuContract.View>() {

    override var playlists: List<Playlist> = emptyList()

    override fun bindView(view: PlaylistMenuContract.View) {
        super.bindView(view)

        loadPlaylists()
    }

    override fun loadPlaylists() {
        launch {
            playlistRepository.getPlaylists(PlaylistQuery.All())
                .collect { playlists ->
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

    override fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean) {
        launch {
            if (ignoreDuplicates || preferenceManager.ignorePlaylistDuplicates) {
                addToPlaylist(playlist, playlistData)
                return@launch
            } else {
                val existingSongs = playlistRepository.getSongsForPlaylist(playlist.id).firstOrNull().orEmpty()
                val songsToAdd = playlistData.getSongs()
                val duplicates = songsToAdd.filter { song -> existingSongs.any { it.id == song.id }}
                if (duplicates.isNotEmpty()) {
                    val deduplicatedPlaylistData = PlaylistData.Songs(songsToAdd - duplicates)
                    view?.onAddToPlaylistWithDuplicates(playlist, playlistData, deduplicatedPlaylistData, duplicates)
                } else {
                    addToPlaylist(playlist, playlistData)
                }
            }
        }
    }

    private fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData) {
        launch {
            val songs = playlistData.getSongs()
            if (songs.isNotEmpty()) {
                try {
                    playlistRepository.addToPlaylist(playlist, songs)
                    view?.onAddedToPlaylist(playlist, playlistData)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add to playlist")
                    view?.onPlaylistAddFailed(Error(e))
                }
            } else {
                view?.onPlaylistAddFailed(UserFriendlyError(context.getString(R.string.playlist_menu_empty_data_message)))
            }
        }
    }

    override fun setIgnorePlaylistDuplicates(ignorePlaylistDuplicates: Boolean) {
        preferenceManager.ignorePlaylistDuplicates = ignorePlaylistDuplicates
    }

    private suspend fun PlaylistData.getSongs(): List<Song> {
        return when (this) {
            is PlaylistData.Songs -> return data
            is PlaylistData.Albums -> songRepository.getSongs(SongQuery.AlbumGroupKeys(data.map { album -> SongQuery.AlbumGroupKey(key = album.groupKey) })).firstOrNull().orEmpty()
            is PlaylistData.AlbumArtists -> songRepository.getSongs(SongQuery.ArtistGroupKeys(data.map { albumArtist -> SongQuery.ArtistGroupKey(key = albumArtist.groupKey) })).firstOrNull().orEmpty()
            is PlaylistData.Genres -> genreRepository.getSongsForGenres(data.map { it.name }, SongQuery.All()).firstOrNull().orEmpty()
            is PlaylistData.Queue -> queueManager.getQueue().map { queueItem -> queueItem.song }
        }
    }
}