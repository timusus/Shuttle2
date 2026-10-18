package com.simplecityapps.shuttle.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.OnboardingPath
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

/** Opens on iOS until a server or the device's music is chosen, since iOS starts with no music to show. */
@ViewModelKey(SourceSetupViewModel::class)
@ContributesIntoMap(AppScope::class)
class SourceSetupViewModel @Inject constructor(
    private val mediaSources: MediaSources,
    isSourceSetupCompleted: IsSourceSetupCompleted,
    private val completeSourceSetup: CompleteSourceSetup,
    importState: SongImportStateProvider,
    private val tryAddServer: TryAddServer,
    private val connectServer: ConnectServer,
    private val analytics: MonetisationAnalytics,
) : ViewModel() {
    private val completed = MutableStateFlow(isSourceSetupCompleted())
    private val serverImport = MutableStateFlow<SourceSetupImport>(SourceSetupImport.NotStarted)

    init {
        // A server connected before the setup existed counts as set up: removing it later doesn't reopen the setup.
        if (!completed.value && hasServer(mediaSources.enabledTypes.value)) complete(path = null)
        viewModelScope.launch {
            importState.providerImportStates.collect { states -> serverImport.update { it.next(states) } }
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
    fun onChooseType(): Boolean = tryAddServer(null)

    /** A server's sign-in succeeded: enable it and import, following that import from here. */
    fun onServerConnected(type: MediaProviderType) {
        serverImport.value = SourceSetupImport.Starting(type)
        connectServer(type)
        complete(OnboardingPath.Server)
    }

    /** Reads the device's music like a server import, so the setup can show it running. */
    fun onUseThisDevice() {
        serverImport.value = SourceSetupImport.Starting(MediaProviderType.Shuttle)
        mediaSources.scanThisDevice()
        complete(OnboardingPath.ThisDevice)
    }

    /** The setup closed, finished or skipped: it doesn't open by itself again. */
    fun onFinish() = complete(OnboardingPath.Skipped)

    /** [path] is recorded if this finishes the first run; null records nothing. */
    private fun complete(path: OnboardingPath?) {
        val firstFinish = !completed.value
        completeSourceSetup()
        completed.value = true
        if (firstFinish && path != null) analytics.onboardingCompleted(path)
    }

    private fun hasServer(types: List<MediaProviderType>) = types.any { !it.isLocal }

    /** Follows the connected server's own import state in [states]; a stale result leaves it be, and its state going ends it. */
    private fun SourceSetupImport.next(states: Map<MediaProviderType, SongImportState>): SourceSetupImport {
        val type = when (this) {
            SourceSetupImport.NotStarted -> return this
            is SourceSetupImport.Starting -> type
            is SourceSetupImport.Running -> type
            is SourceSetupImport.Finished -> type
        }
        val state = states[type]
        return when {
            state is SongImportState.ImportProgress ->
                SourceSetupImport.Running(type, state.message, state.progress?.asFloat())

            state is SongImportState.ImportComplete && this is SourceSetupImport.Running ->
                SourceSetupImport.Finished(type, state.error)

            // Its state went with it: the source was removed, or its first import cancelled. Over either way, so the setup
            // doesn't wait on it
            state == null && this is SourceSetupImport.Running ->
                SourceSetupImport.Finished(type, error = null)

            else -> this
        }
    }
}
