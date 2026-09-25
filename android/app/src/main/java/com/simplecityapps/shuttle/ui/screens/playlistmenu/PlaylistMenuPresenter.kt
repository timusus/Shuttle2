package com.simplecityapps.shuttle.ui.screens.playlistmenu

import android.content.Context
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val preferenceManager: GeneralPreferenceManager,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
) : BasePresenter<PlaylistMenuContract.View>(),
    PlaylistMenuContract.Presenter {
    override var playlists: List<Playlist> = emptyList()
    private val _playlistsState = MutableStateFlow(emptyList<Playlist>())
    val playlistsState = _playlistsState.asStateFlow()

    override fun bindView(view: PlaylistMenuContract.View) {
        super.bindView(view)

        loadPlaylists()
    }

    override fun loadPlaylists() {
        launch {
            playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null))
                .collect { playlists ->
                    this@PlaylistMenuPresenter.playlists = playlists
                    this@PlaylistMenuPresenter._playlistsState.value = playlists
                }
        }
    }

    override fun createPlaylist(
        name: String,
        playlistData: PlaylistData?
    ) {
        launch {
            val playlist = createPlaylistUseCase(name, playlistData?.toMediaSelection())
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
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success -> view?.onAddedToPlaylist(playlist, playlistData)

                is AddToPlaylist.Result.DuplicatesFound -> view?.onAddToPlaylistWithDuplicates(
                    playlist,
                    playlistData,
                    PlaylistData.Songs(result.nonDuplicates),
                    result.duplicates,
                )

                is AddToPlaylist.Result.Failure -> view?.onPlaylistAddFailed(
                    result.message?.let { Error(it) }
                        ?: UserFriendlyError(context.getString(R.string.playlist_menu_empty_data_message)),
                )
            }
        }
    }

    override fun setIgnorePlaylistDuplicates(ignorePlaylistDuplicates: Boolean) {
        preferenceManager.ignorePlaylistDuplicates = ignorePlaylistDuplicates
    }
}
