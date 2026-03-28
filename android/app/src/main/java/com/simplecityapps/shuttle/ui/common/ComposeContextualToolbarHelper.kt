package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.collections.distinct
import kotlin.collections.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class ComposeContextualToolbarHelper {

    var toolbar: Toolbar? = null
    var contextualToolbar: Toolbar? = null

    private val _selectedSongsState = MutableStateFlow(emptySet<Song>())
    val selectedSongsState = _selectedSongsState.asStateFlow()
    val selectedSongCountState = _selectedSongsState.asStateFlow()
        .map { selectedSongs -> selectedSongs.size }

    fun toggleSongSelection(song: Song) {
        _selectedSongsState.value = if (_selectedSongsState.value.contains(song)) {
            _selectedSongsState.value - song
        } else {
            _selectedSongsState.value + song
        }
    }

    fun clearSelection() {
        _selectedSongsState.value = emptySet()
    }

    fun isSelecting() = _selectedSongsState.value.isNotEmpty()

    fun show() {
        contextualToolbar?.let { contextualToolbar ->
            toolbar?.isVisible = false
            contextualToolbar.isVisible = true
            contextualToolbar.setNavigationOnClickListener {
                hide()
            }
        } ?: Timber.e("Failed to show contextual toolbar: toolbar null")
    }

    fun hide() {
        toolbar?.isVisible = true
        contextualToolbar?.isVisible = false
        contextualToolbar?.setNavigationOnClickListener(null)
        clearSelection()
    }

    fun selectedSongsMediaProviders(): List<MediaProviderType> = selectedSongsState
        .value
        .map { it.mediaProvider }
        .distinct()
}
