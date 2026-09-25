package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.simplecityapps.shuttle.model.MediaProviderType

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
    val viewModel = hiltViewModel<ServerSignInViewModel, ServerSignInViewModel.Factory>(
        viewModelStoreOwner = rememberViewModelStoreOwner(),
        key = type.name,
    ) { it.create(type) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnConnected by rememberUpdatedState(onConnected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ServerSignInEvent.Connected -> currentOnConnected(type)
                ServerSignInEvent.Finished -> currentOnDismiss()
            }
        }
    }

    val actions = remember(viewModel) {
        ServerSignInActions(
            onAddressChange = viewModel::onAddressChange,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onAuthCodeChange = viewModel::onAuthCodeChange,
            onRememberPasswordChange = viewModel::onRememberPasswordChange,
            onAuthenticate = viewModel::onAuthenticate,
            onRetry = viewModel::onRetry,
            onDismiss = { currentOnDismiss() },
        )
    }
    ServerSignInDialog(uiState, actions)
}
