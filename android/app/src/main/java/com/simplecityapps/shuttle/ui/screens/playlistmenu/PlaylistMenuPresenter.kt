package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.Context
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

interface PlaylistMenuContract {
    interface View : CreatePlaylistDialogFragment.Listener {
        fun onPlaylistCreated(playlist: Playlist)

        fun onAddedToPlaylist(
            playlist: Playlist,
            playlistData: PlaylistData
        )

        fun onPlaylistAddFailed(error: Error)

        fun showCreatePlaylistDialog(playlistData: PlaylistData)

        fun onAddToPlaylistWithDuplicates(
            playlist: Playlist,
            playlistData: PlaylistData,
            deduplicatedPlaylistData: PlaylistData.Songs,
            duplicates: List<com.simplecityapps.shuttle.model.Song>
        )
    }

    interface Presenter : BaseContract.Presenter<View> {
        var playlists: List<Playlist>

        fun loadPlaylists()

        fun createPlaylist(
            name: String,
            playlistData: PlaylistData?
        )

        fun addToPlaylist(
            playlist: Playlist,
            playlistData: PlaylistData,
            ignoreDuplicates: Boolean = false
        )

        fun setIgnorePlaylistDuplicates(ignorePlaylistDuplicates: Boolean)
    }
}

class PlaylistMenuPresenter
@Inject
constructor(
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
            playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null))
                .collect { playlists ->
                    this@PlaylistMenuPresenter.playlists = playlists
                }
        }
    }

    override fun createPlaylist(
        name: String,
        playlistData: PlaylistData?
    ) {
        launch {
            val songs = playlistData?.getSongs()
            val playlist =
                playlistRepository.createPlaylist(
                    name = name,
                    mediaProviderType = MediaProviderType.Shuttle,
                    songs = songs,
                    externalId = null
                )

            if (playlistData != null) {
                view?.onAddedToPlaylist(playlist, playlistData)
            } else {
                view?.onPlaylistCreated(playlist)
            }
        }
    }

    override fun addToPlaylist(
        playlist: Playlist,
        playlistData: PlaylistData,
        ignoreDuplicates: Boolean
    ) {
        launch {
            if (ignoreDuplicates || preferenceManager.ignorePlaylistDuplicates) {
                addToPlaylist(playlist, playlistData)
                return@launch
            } else {
                val existingSongs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty()
                val songsToAdd = playlistData.getSongs()
                val duplicates = songsToAdd.filter { song -> existingSongs.any { it.song.id == song.id } }
                if (duplicates.isNotEmpty()) {
                    val deduplicatedPlaylistData = PlaylistData.Songs(songsToAdd - duplicates)
                    view?.onAddToPlaylistWithDuplicates(playlist, playlistData, deduplicatedPlaylistData, duplicates)
                } else {
                    addToPlaylist(playlist, playlistData)
                }
            }
        }
    }

    private fun addToPlaylist(
        playlist: Playlist,
        playlistData: PlaylistData
    ) {
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

    private suspend fun PlaylistData.getSongs(): List<com.simplecityapps.shuttle.model.Song> {
        return when (this) {
            is PlaylistData.Songs -> return data
            is PlaylistData.Albums -> {
                songRepository.getSongs(SongQuery.AlbumGroupKeys(data.map { album -> SongQuery.AlbumGroupKey(key = album.groupKey) }))
                    .firstOrNull()
                    .orEmpty()
                    .sortedWith(SongSortOrder.Default.comparator)
            }
            is PlaylistData.AlbumArtists -> {
                songRepository.getSongs(SongQuery.ArtistGroupKeys(data.map { albumArtist -> SongQuery.ArtistGroupKey(key = albumArtist.groupKey) }))
                    .firstOrNull()
                    .orEmpty()
                    .sortedWith(SongSortOrder.Default.comparator)
            }
            is PlaylistData.Genres -> {
                genreRepository.getSongsForGenres(
                    genres = data.map { it.name },
                    songQuery = SongQuery.All()
                ).firstOrNull()
                    .orEmpty()
                    .sortedWith(SongSortOrder.Default.comparator)
            }
            is PlaylistData.Queue -> queueManager.getQueue().map { queueItem -> queueItem.song }
        }
    }
}
