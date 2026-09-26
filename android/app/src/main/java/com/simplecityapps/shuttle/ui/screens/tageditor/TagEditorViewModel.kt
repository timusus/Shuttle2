package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The tag editor for one song or several: reads the tags from the songs' files, lets the user change them, and
 * writes only the changed fields back.
 */
@HiltViewModel(assistedFactory = TagEditorViewModel.Factory::class)
class TagEditorViewModel @AssistedInject constructor(
    @Assisted songIds: List<Long>,
    observeSongs: ObserveSongs,
    private val readSongTags: ReadSongTags,
    private val writeSongTags: WriteSongTags,
    private val tagFileAccess: TagFileAccess,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(songIds: List<Long>): TagEditorViewModel
    }

    private val _uiState = MutableStateFlow<TagEditorUiState>(TagEditorUiState.Reading(TagProgress(0, songIds.size)))
    private val events = PendingEvents<TagEditorEvent>()

    val uiState: StateFlow<TagEditorUiState> = combine(_uiState, events.flow) { state, events ->
        if (state is TagEditorUiState.Editing) state.copy(events = events) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    private var editable: List<EditableSong> = emptyList()

    /** The edits a save holds back while the user is asked for consent to write the files. */
    private var pendingEdits: Map<TagField, String>? = null

    init {
        viewModelScope.launch {
            val songs = observeSongs(SongQuery.SongIds(songIds)).first()
                .sortedBy { songIds.indexOf(it.id) }
            val tags = readSongTags(songs) { read, total -> _uiState.value = TagEditorUiState.Reading(TagProgress(read, total)) }
            editable = tags.editable
            _uiState.value = if (editable.isEmpty()) {
                TagEditorUiState.Unreadable
            } else {
                TagEditorUiState.Editing(songCount = editable.size, fields = tagFields(editable.map { it.file }), skipped = tags.skipped)
            }
        }
    }

    fun onFieldChange(
        field: TagField,
        text: String,
    ) = updateField(field) { it.copy(text = text) }

    fun onFieldReset(field: TagField) = updateField(field) { it.copy(text = it.initial) }

    fun onSave() {
        val editing = _uiState.value as? TagEditorUiState.Editing ?: return
        if (editing.writing != null || !editing.hasChanges) return
        val edits = editing.fields.edits()
        _uiState.value = editing.copy(writing = TagProgress(0, editable.size))
        viewModelScope.launch {
            val consent = tagFileAccess.writeConsent(editable.map { it.song })
            if (consent == null) {
                write(edits)
            } else {
                pendingEdits = edits
                events.post(TagEditorEvent.RequestWriteConsent(consent))
            }
        }
    }

    /** The user's answer to [TagEditorEvent.RequestWriteConsent]: the save goes ahead, or goes back to editing. */
    fun onWriteConsent(granted: Boolean) {
        val edits = pendingEdits ?: return
        pendingEdits = null
        if (granted) {
            viewModelScope.launch { write(edits) }
        } else {
            _uiState.update { state -> (state as? TagEditorUiState.Editing)?.copy(writing = null) ?: state }
        }
    }

    private suspend fun write(edits: Map<TagField, String>) {
        val result = writeSongTags(editable, edits) { written, total ->
            _uiState.update { state -> (state as? TagEditorUiState.Editing)?.copy(writing = TagProgress(written, total)) ?: state }
        }
        events.post(TagEditorEvent.Saved(result))
    }

    fun onEventHandled(id: Long) = events.consume(id)

    private fun updateField(
        field: TagField,
        transform: (TagFieldState) -> TagFieldState,
    ) {
        _uiState.update { state ->
            if (state !is TagEditorUiState.Editing || state.writing != null) return@update state
            state.copy(fields = state.fields.map { if (it.field == field) transform(it) else it })
        }
    }
}
