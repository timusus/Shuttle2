package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class MediaImportObserver @Inject constructor(
    mediaImporter: MediaImporter,
    @AppCoroutineScope scope: CoroutineScope
) : SongImportStateProvider {
    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
    override val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    private val _playlistImportState = MutableStateFlow<PlaylistImportState>(PlaylistImportState.Idle)
    val playlistImportState: StateFlow<PlaylistImportState> = _playlistImportState.asStateFlow()

    init {
        scope.launch {
            mediaImporter.importEvents.collect { event ->
                when (event) {
                    is MediaImporter.ImportEvent.Started -> {
                        _songImportState.value = SongImportState.ImportProgress(
                            providerType = event.providerType,
                            message = null,
                            progress = null
                        )
                    }

                    is MediaImporter.ImportEvent.SongImportProgress -> {
                        _songImportState.value = SongImportState.ImportProgress(event.providerType, event.message, event.progress)
                    }

                    is MediaImporter.ImportEvent.SongImportComplete -> {
                        _songImportState.value = SongImportState.ImportComplete(event.providerType, null)
                    }

                    is MediaImporter.ImportEvent.SongImportFailed -> {
                        _songImportState.value = SongImportState.ImportComplete(event.providerType, event.message)
                    }

                    is MediaImporter.ImportEvent.PlaylistImportProgress -> {
                        _playlistImportState.value = PlaylistImportState.ImportProgress(event.providerType, event.message, event.progress)
                    }

                    is MediaImporter.ImportEvent.PlaylistImportComplete -> {
                        _playlistImportState.value = PlaylistImportState.ImportComplete(event.providerType, null)
                    }

                    is MediaImporter.ImportEvent.PlaylistImportFailed -> {
                        _playlistImportState.value = PlaylistImportState.ImportComplete(event.providerType, event.message)
                    }

                    MediaImporter.ImportEvent.AllComplete -> {
                        // Anyone interested in this event could derive it by observing both state flows
                    }
                }
            }
        }
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
