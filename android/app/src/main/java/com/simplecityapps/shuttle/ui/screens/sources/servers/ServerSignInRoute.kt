package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.simplecityapps.shuttle.R
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

    // A LAN address needs Android 17's local-network permission (once targetSdk is 37); asked for just before connecting
    val context = LocalContext.current
    var localNetworkDenied by remember { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val proceed = afterGrant
        afterGrant = null
        if (granted) proceed?.invoke() else localNetworkDenied = true
    }
    val currentAddress by rememberUpdatedState(uiState.form.address)
    val withLocalNetworkPermission: (() -> Unit) -> Unit = { connect ->
        if (LocalNetworkPermission.shouldRequest(type, currentAddress, LocalNetworkPermission.isEnforced(context), LocalNetworkPermission.isGranted(context))) {
            afterGrant = connect
            permissionLauncher.launch(LocalNetworkPermission.NAME)
        } else {
            connect()
        }
    }

    val actions = remember(viewModel, uriHandler) {
        ServerSignInActions(
            onAddressChange = viewModel::onAddressChange,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onRememberPasswordChange = viewModel::onRememberPasswordChange,
            onAuthenticate = { withLocalNetworkPermission(viewModel::onAuthenticate) },
            onRetry = {
                localNetworkDenied = false
                viewModel.onRetry()
            },
            onDismiss = { currentOnDismiss() },
            onUseQuickConnect = { withLocalNetworkPermission(viewModel::onUseQuickConnect) },
            onCancelQuickConnect = viewModel::onCancelQuickConnect,
            onOpenUrl = openUrl,
            onChooseServer = viewModel::onChooseServer,
            onCancelPin = viewModel::onCancelPin,
            onShowAdvancedChange = viewModel::onShowAdvancedChange,
            onAddHeader = viewModel::onAddHeader,
            onHeaderChange = viewModel::onHeaderChange,
            onRemoveHeader = viewModel::onRemoveHeader,
            onTrustCertificate = viewModel::onTrustCertificate,
        )
    }
    val shownState = if (localNetworkDenied) {
        uiState.copy(step = ServerSignInStep.Failed(stringResource(R.string.media_provider_local_network_denied)))
    } else {
        uiState
    }
    ServerSignInDialog(shownState, actions)
}
