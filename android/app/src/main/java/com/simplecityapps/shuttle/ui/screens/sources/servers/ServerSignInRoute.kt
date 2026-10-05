package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * Shows a [type] server's sign-in dialog. Its ViewModel lives only as long as the dialog, so each opening starts
 * from the saved login. [onConnected] runs as soon as the server is signed in; the dialog closes shortly after,
 * through [onDismiss].
 */
@Composable
fun ServerSignInRoute(
    type: MediaProviderType,
    onConnected: (MediaProviderType) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = assistedMetroViewModel<ServerSignInViewModel, ServerSignInViewModel.Factory>(
        viewModelStoreOwner = rememberViewModelStoreOwner(),
        key = type.name,
    ) { create(type) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnConnected by rememberUpdatedState(onConnected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val uriHandler = LocalUriHandler.current
    // No browser to open leaves the PIN on screen, for plex.tv/link on another device
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            ServerSignInEvent.Connected -> currentOnConnected(type)
            ServerSignInEvent.Finished -> currentOnDismiss()
            is ServerSignInEvent.OpenUrl -> openUrl(event.url)
        }
    }

    val actions = remember(viewModel, uriHandler) {
        ServerSignInActions(
            onAddressChange = viewModel::onAddressChange,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onRememberPasswordChange = viewModel::onRememberPasswordChange,
            onAuthenticate = viewModel::onAuthenticate,
            onRetry = viewModel::onRetry,
            onDismiss = { currentOnDismiss() },
            onUseQuickConnect = viewModel::onUseQuickConnect,
            onCancelQuickConnect = viewModel::onCancelQuickConnect,
            onOpenUrl = openUrl,
            onChooseServer = viewModel::onChooseServer,
            onCancelPin = viewModel::onCancelPin,
        )
    }
    ServerSignInDialog(uiState, actions)
}
