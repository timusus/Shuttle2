package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class MediaImportObserver @Inject constructor(
    mediaImporter: MediaImporter
) : MediaImporter.Listener {

    init {
        mediaImporter.listeners.add(this)
    }

    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
    val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    private val _playlistImportState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val playlistImportState: StateFlow<PlaylistImportState> = _playlistImportState.asStateFlow()

    override fun onStart(providerType: MediaProviderType) {
        _songImportState.value = SongImportState.ImportProgress(
            providerType = providerType,
            message = null,
            progress = null
        )
    }

    override fun onSongImportProgress(
        providerType: MediaProviderType,
        message: String,
        progress: Progress?
    ) {
        _songImportState.value = SongImportState.ImportProgress(providerType, message, progress)
    }

    override fun onSongImportComplete(providerType: MediaProviderType) {
        _songImportState.value = SongImportState.ImportComplete(providerType, null)
    }

    override fun onSongImportFailed(providerType: MediaProviderType, message: String?) {
        _songImportState.value = SongImportState.ImportComplete(providerType, message)
    }

    override fun onPlaylistImportProgress(
        providerType: MediaProviderType,
        message: String,
        progress: Progress?
    ) {
        _playlistImportState.value = PlaylistImportState.ImportProgress(providerType, message, progress)
    }

    override fun onPlaylistImportComplete(providerType: MediaProviderType) {
        _playlistImportState.value = PlaylistImportState.ImportComplete(providerType, null)
    }

    override fun onPlaylistImportFailed(providerType: MediaProviderType, message: String?) {
        _playlistImportState.value = PlaylistImportState.ImportComplete(providerType, message)
    }

    override fun onAllComplete() {
        // Anyone interested in this event could derive it by observing both state flows
    }
}

sealed class SongImportState {
    data object Idle : SongImportState()
    data class ImportProgress(
        val providerType: MediaProviderType,
        val message: String?,
        val progress: Progress?
    ) : SongImportState()

    data class ImportComplete(val providerType: MediaProviderType, val error: String?) : SongImportState()
}

sealed class PlaylistImportState {
    data object Idle : PlaylistImportState()
    data class ImportProgress(
        val providerType: MediaProviderType,
        val message: String?,
        val progress: Progress?
    ) : PlaylistImportState()

    data class ImportComplete(val providerType: MediaProviderType, val error: String?) : PlaylistImportState()
}
