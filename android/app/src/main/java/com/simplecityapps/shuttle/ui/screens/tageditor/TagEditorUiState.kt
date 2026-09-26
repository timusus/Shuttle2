package com.simplecityapps.shuttle.ui.screens.tageditor

import android.content.IntentSender
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.PendingEvent

data class TagProgress(
    val done: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

sealed interface TagEditorUiState {
    data class Reading(val progress: TagProgress) : TagEditorUiState

    /** None of the songs' files could be read, or none of them can be written. */
    data object Unreadable : TagEditorUiState

    data class Editing(
        /** How many songs a save writes. */
        val songCount: Int,
        val fields: List<TagFieldState>,
        /** Songs the save leaves alone, because their files couldn't be read or written. */
        val skipped: List<Song> = emptyList(),
        /** Set while the save writes the files. */
        val writing: TagProgress? = null,
        /** What the save still needs the screen to do: ask for consent, or report the result and close. */
        val events: List<PendingEvent<TagEditorEvent>> = emptyList(),
    ) : TagEditorUiState {
        val hasChanges: Boolean get() = fields.any { it.changed }
    }
}

sealed interface TagEditorEvent {
    data class Saved(val result: TagWriteResult) : TagEditorEvent

    /** The system has to ask the user before the save can change some of the files; answer with [TagEditorViewModel.onWriteConsent]. */
    data class RequestWriteConsent(val intentSender: IntentSender) : TagEditorEvent
}
