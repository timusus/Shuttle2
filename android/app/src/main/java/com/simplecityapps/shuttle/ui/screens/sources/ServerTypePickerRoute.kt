package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute

/**
 * "Connect a server" from Home or Library (#487): the type picker, then the chosen type's existing sign-in dialog,
 * without ever leaving for Settings. [onDismissRequest] closes the whole flow, whether from the picker or the
 * sign-in dialog's own dismissal (cancelled, or the "Authentication Successful" message finishing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTypePickerRoute(onDismissRequest: () -> Unit) {
    val viewModel: ServerTypePickerViewModel = hiltViewModel()
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }

    val signingInType = signingIn
    if (signingInType == null) {
        ServerTypePickerSheet(
            onTypeSelected = { type -> if (viewModel.onAddServer()) signingIn = type },
            onDismissRequest = onDismissRequest,
        )
    } else {
        ServerSignInRoute(
            type = signingInType,
            onConnected = viewModel::onServerConnected,
            onDismiss = onDismissRequest,
        )
    }
}
