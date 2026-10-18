package com.simplecityapps.shuttle.ui.screens.sources

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.entitlement.ObserveAddServerResumes
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface ServerTypePickerEvent {
    /** Open [type]'s sign-in: the add the paywall interrupted carries on. */
    data class OpenSignIn(val type: MediaProviderType) : ServerTypePickerEvent
}

/** Backs the server-type picker sheet; the paywall gate and connect side effect match [SourcesViewModel]. */
@ViewModelKey(ServerTypePickerViewModel::class)
@ContributesIntoMap(AppScope::class)
class ServerTypePickerViewModel @Inject constructor(
    private val tryAddServer: TryAddServer,
    observeAddServerResumes: ObserveAddServerResumes,
    private val connectServer: ConnectServer,
) : ViewModel() {
    /** The paywall a refused [onAddServer] opened closed with the add allowed: carry on with the type it was for. */
    val events: Flow<ServerTypePickerEvent> = observeAddServerResumes().map(ServerTypePickerEvent::OpenSignIn)

    /** Whether [type]'s sign-in dialog may open; false opens the paywall instead. */
    fun onAddServer(type: MediaProviderType): Boolean = tryAddServer(type)

    /** A server's sign-in dialog succeeded. */
    fun onServerConnected(type: MediaProviderType) = connectServer(type)
}
