package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MediaActionsUiState(
    /** The playlists offered by the add-to-playlist picker; empty while the picker is closed. */
    val playlists: List<Playlist> = emptyList(),
    /** Results the screen still has to act on; [MediaActionResult.None] is never posted. */
    val events: List<PendingEvent<MediaActionResult>> = emptyList(),
)

/**
 * Runs a screen's media actions through [MediaActionHandler] and hands each result to the screen once, so every
 * Compose destination shares one action path: its sheets, selection toolbar and snackbar buttons all [dispatch]
 * here. Scoped to the destination's nav entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaActionsViewModel @Inject constructor(
    private val handler: MediaActionHandler,
    private val availableMediaActions: AvailableMediaActions,
    observePlaylists: ObservePlaylists,
) : ViewModel() {
    private val playlistPickerShown = MutableStateFlow(false)

    private val events = PendingEvents<MediaActionResult>()

    val uiState: StateFlow<MediaActionsUiState> = combine(
        // Observed only while the picker shows, so every destination doesn't keep a playlist query running.
        playlistPickerShown.flatMapLatest { shown -> if (shown) observePlaylists() else flowOf(emptyList()) },
        events.flow,
    ) { playlists, events -> MediaActionsUiState(playlists, events) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaActionsUiState())

    fun dispatch(action: MediaAction) {
        viewModelScope.launch {
            val result = handler.handle(action)
            if (result != MediaActionResult.None) events.post(result)
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)

    fun onPlaylistPickerShown(shown: Boolean) {
        playlistPickerShown.value = shown
    }

    fun availableActions(selection: MediaSelection): Flow<List<MediaActionType>> = availableMediaActions(selection)
}
