package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Runs a screen's media actions through [MediaActionHandler] and hands each result to the screen once, so every
 * Compose destination shares one action path: its sheets, selection toolbar and snackbar buttons all [dispatch]
 * here. Scoped to the destination's nav entry.
 */
@HiltViewModel
class MediaActionsViewModel @Inject constructor(
    private val handler: MediaActionHandler,
    private val availableMediaActions: AvailableMediaActions,
    playlistRepository: PlaylistRepository,
) : ViewModel() {
    /** The playlists offered by the add-to-playlist picker. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository
        .getPlaylists(PlaylistQuery.All(mediaProviderType = null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = Channel<MediaActionResult>(Channel.BUFFERED)

    /** One-off results, delivered once each; [MediaActionResult.None] is not sent. */
    val results: Flow<MediaActionResult> = _results.receiveAsFlow()

    fun dispatch(action: MediaAction) {
        viewModelScope.launch {
            val result = handler.handle(action)
            if (result != MediaActionResult.None) _results.send(result)
        }
    }

    fun availableActions(selection: MediaSelection): Flow<List<MediaActionType>> = availableMediaActions(selection)
}
