package com.simplecityapps.shuttle.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.sources.ConnectServer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShellUiState(
    /**
     * The tab the app opens on: Home when Show Home on launch is on, else Library. Read once, so a change in
     * Settings applies from the next launch, and back at the root of another tab keeps returning to this one.
     */
    val startTab: ShellTab,
    val events: List<PendingEvent<ShellEvent>> = emptyList(),
)

sealed interface ShellEvent {
    /** The server rejected [type]'s session mid-session and it was cleared, so the user has to sign in again (#595). */
    data class ServerSignedOut(val type: MediaProviderType) : ShellEvent
}

/** The shell's launch state, from Settings, and the server sign-outs it tells the user about. */
@ViewModelKey(ShellViewModel::class)
@ContributesIntoMap(AppScope::class)
class ShellViewModel @Inject constructor(
    readSetting: ReadSetting,
    serverSessions: ServerSessions,
    private val connectServer: ConnectServer,
) : ViewModel() {
    private val startTab = if (readSetting(AppearanceSettings.ShowHomeOnLaunch)) ShellTab.Home else ShellTab.Library
    private val events = PendingEvents<ShellEvent>()

    val uiState: StateFlow<ShellUiState> = events.flow
        .map { pending -> ShellUiState(startTab = startTab, events = pending) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState(startTab))

    init {
        viewModelScope.launch {
            serverSessions.expired.collect { events.post(ShellEvent.ServerSignedOut(it)) }
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /** The sign-in dialog opened from a sign-out snackbar succeeded. */
    fun onServerConnected(type: MediaProviderType) = connectServer(type)
}
