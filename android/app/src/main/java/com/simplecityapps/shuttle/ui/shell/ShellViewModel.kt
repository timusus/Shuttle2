package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ShellQueueRow(
    val uid: Long,
    val title: String,
    val subtitle: String?,
    val isCurrent: Boolean,
)

/**
 * What the shell's player needs from the queue.
 *
 * [hasQueue] is null until the queue has been restored (or holds items), so on a cold start the
 * saved player level stands until the first real emission instead of flashing the mini player.
 */
@Immutable
data class ShellQueueUiState(
    val hasQueue: Boolean?,
    val current: ShellQueueRow?,
    val items: List<ShellQueueRow>,
) {
    companion object {
        val Unknown = ShellQueueUiState(hasQueue = null, current = null, items = emptyList())
    }
}

/** Shell-level state: for now, whether anything is queued (docs/architecture/app-shell.md, section 1). */
@HiltViewModel
class ShellViewModel @Inject constructor(
    queueOperations: QueueOperations,
) : ViewModel() {
    val queue: StateFlow<ShellQueueUiState> =
        queueOperations.queueStateFlow
            .map { it.toShellQueueUiState() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), queueOperations.queueStateFlow.value.toShellQueueUiState())
}

internal fun QueueState.toShellQueueUiState(): ShellQueueUiState {
    if (items.isEmpty() && !isRestored) return ShellQueueUiState.Unknown
    val rows = items.map { item ->
        ShellQueueRow(
            uid = item.uid,
            title = item.song.name.orEmpty(),
            subtitle = item.song.friendlyArtistName ?: item.song.albumArtist,
            isCurrent = item.isCurrent,
        )
    }
    return ShellQueueUiState(hasQueue = rows.isNotEmpty(), current = rows.firstOrNull { it.isCurrent }, items = rows)
}
