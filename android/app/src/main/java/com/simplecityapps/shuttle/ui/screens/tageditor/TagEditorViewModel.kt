package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    ) : TagEditorUiState {
        val hasChanges: Boolean get() = fields.any { it.changed }
    }
}

sealed interface TagEditorEvent {
    data class Saved(val result: TagWriteResult) : TagEditorEvent
}

/**
 * The tag editor for one song or several: reads the tags from the songs' files, lets the user change them, and
 * writes only the changed fields back.
 */
@HiltViewModel(assistedFactory = TagEditorViewModel.Factory::class)
class TagEditorViewModel @AssistedInject constructor(
    @Assisted songIds: List<Long>,
    songRepository: SongRepository,
    private val readSongTags: ReadSongTags,
    private val writeSongTags: WriteSongTags,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(songIds: List<Long>): TagEditorViewModel
    }

    private val _uiState = MutableStateFlow<TagEditorUiState>(TagEditorUiState.Reading(TagProgress(0, songIds.size)))
    val uiState: StateFlow<TagEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<TagEditorEvent>(Channel.BUFFERED)
    val events: Flow<TagEditorEvent> = _events.receiveAsFlow()

    private var editable: List<EditableSong> = emptyList()

    init {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.SongIds(songIds)).filterNotNull().first()
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
            val result = writeSongTags(editable, edits) { written, total ->
                _uiState.update { state -> (state as? TagEditorUiState.Editing)?.copy(writing = TagProgress(written, total)) ?: state }
            }
            _events.send(TagEditorEvent.Saved(result))
        }
    }

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
