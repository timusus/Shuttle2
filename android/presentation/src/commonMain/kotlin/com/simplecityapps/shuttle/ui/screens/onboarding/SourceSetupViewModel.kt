package com.simplecityapps.shuttle.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.ConnectServer
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.isLocal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far the import of a server connected during setup, or of this device's music, has got. */
sealed interface SourceSetupImport {
    /** No server has been connected in this setup yet. */
    data object NotStarted : SourceSetupImport

    /** [type] is connected and its import asked for, but the importer hasn't reported on it yet. */
    data class Starting(val type: MediaProviderType) : SourceSetupImport

    /** [type]'s songs are importing: the provider's latest [message] and how far through it is, if it knows. */
    data class Running(val type: MediaProviderType, val message: String?, val fraction: Float?) : SourceSetupImport

    /** [type]'s songs have imported, or failed to with [error]. */
    data class Finished(val type: MediaProviderType, val error: String?) : SourceSetupImport
}

data class SourceSetupUiState(
    /** Nothing to play from and the setup never finished or skipped: the app opens the setup at launch. */
    val firstRun: Boolean = false,
    val serverImport: SourceSetupImport = SourceSetupImport.NotStarted,
)

/**
 * Adding a media server (#624), from the first-run setup or Sources' Add Source: whether the setup opens at launch,
 * the paywall gate and connect side effect [com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerViewModel]
 * has, and the connected server's import, so the setup can show it running and let the user carry on meanwhile.
 *
 * Android has no first run (#379: the Library's empty state asks for the music permission instead); iOS has no music
 * on the device to start from, so it opens this setup until a server is connected, the device's own music is chosen
 * (Files and the app's Documents folder, #590) or the user skips it.
 */
@ViewModelKey(SourceSetupViewModel::class)
@ContributesIntoMap(AppScope::class)
class SourceSetupViewModel @Inject constructor(
    private val mediaSources: MediaSources,
    isSourceSetupCompleted: IsSourceSetupCompleted,
    private val completeSourceSetup: CompleteSourceSetup,
    importState: SongImportStateProvider,
    private val tryAddServer: TryAddServer,
    private val connectServer: ConnectServer,
) : ViewModel() {
    private val completed = MutableStateFlow(isSourceSetupCompleted())
    private val serverImport = MutableStateFlow<SourceSetupImport>(SourceSetupImport.NotStarted)

    init {
        // A server connected before the setup existed counts as set up: removing it later doesn't reopen the setup.
        if (!completed.value && hasServer(mediaSources.enabledTypes.value)) complete()
        viewModelScope.launch {
            importState.songImportState.collect { state -> serverImport.update { it.next(state) } }
        }
    }

    val uiState: StateFlow<SourceSetupUiState> =
        combine(mediaSources.enabledTypes, completed, serverImport) { types, completed, serverImport ->
            SourceSetupUiState(firstRun = !completed && !hasServer(types), serverImport = serverImport)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SourceSetupUiState(firstRun = !completed.value && !hasServer(mediaSources.enabledTypes.value), serverImport = serverImport.value),
        )

    /** Whether a chosen type's sign-in may open; false opens the paywall instead. */
    fun onChooseType(): Boolean = tryAddServer()

    /** A server's sign-in succeeded: enable it and import, following that import from here. */
    fun onServerConnected(type: MediaProviderType) {
        serverImport.value = SourceSetupImport.Starting(type)
        connectServer(type)
        complete()
    }

    /**
     * The music on this device was chosen (iOS's "On this iPhone", #590): read it, following that import from here, as
     * the setup does a server's. The setup is done, as the device's music is the library's source from now on.
     */
    fun onUseThisDevice() {
        serverImport.value = SourceSetupImport.Starting(MediaProviderType.Shuttle)
        mediaSources.scanThisDevice()
        complete()
    }

    /** The setup closed, finished or skipped: it doesn't open by itself again. */
    fun onFinish() = complete()

    private fun complete() {
        completeSourceSetup()
        completed.value = true
    }

    private fun hasServer(types: List<MediaProviderType>) = types.any { !it.isLocal }

    /** Follows the connected server's import; any other provider's progress, or a stale result, leaves it be. */
    private fun SourceSetupImport.next(state: SongImportState): SourceSetupImport {
        val type = when (this) {
            SourceSetupImport.NotStarted -> return this
            is SourceSetupImport.Starting -> type
            is SourceSetupImport.Running -> type
            is SourceSetupImport.Finished -> type
        }
        return when {
            state is SongImportState.ImportProgress && state.providerType == type ->
                SourceSetupImport.Running(type, state.message, state.progress?.asFloat())

            state is SongImportState.ImportComplete && state.providerType == type && this is SourceSetupImport.Running ->
                SourceSetupImport.Finished(type, state.error)

            else -> this
        }
    }
}
