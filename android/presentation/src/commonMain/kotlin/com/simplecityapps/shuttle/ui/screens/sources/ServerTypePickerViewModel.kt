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

/** Backs the server-type picker sheet (#487): the paywall gate and the connect side effect [SourcesViewModel] also uses. */
@ViewModelKey(ServerTypePickerViewModel::class)
@ContributesIntoMap(AppScope::class)
class ServerTypePickerViewModel @Inject constructor(
    private val tryAddServer: TryAddServer,
    observeAddServerResumes: ObserveAddServerResumes,
    private val connectServer: ConnectServer,
) : ViewModel() {
    /** The paywall a refused [onAddServer] opened closed with the add allowed: carry on with the type chosen. */
    val addResumes: Flow<Unit> = observeAddServerResumes()

    /** Whether a chosen type's sign-in dialog may open; false opens the paywall instead. */
    fun onAddServer(): Boolean = tryAddServer()

    /** A server's sign-in dialog succeeded. */
    fun onServerConnected(type: MediaProviderType) = connectServer(type)
}
