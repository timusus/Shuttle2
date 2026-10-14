package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * "Connect a server" from Home or Library (#487): the type picker, then the chosen type's existing sign-in dialog,
 * without ever leaving for Settings. [onDismissRequest] closes the whole flow, whether from the picker or the
 * sign-in dialog's own dismissal (cancelled, or the "Authentication Successful" message finishing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTypePickerRoute(onDismissRequest: () -> Unit) {
    val viewModel: ServerTypePickerViewModel = metroViewModel()
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }

    // An add the paywall interrupted carries on once the paywall closes with the add allowed
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ServerTypePickerEvent.OpenSignIn -> signingIn = event.type
            }
        }
    }

    ServerTypePickerFlow(
        signingIn = signingIn,
        onTypeSelected = { type ->
            viewModel.onAddServer(type).also { allowed -> if (allowed) signingIn = type }
        },
        onDismissRequest = onDismissRequest,
        signIn = { type ->
            ServerSignInRoute(
                type = type,
                onConnected = viewModel::onServerConnected,
                onDismiss = onDismissRequest,
            )
        },
    )
}

/** The picker until a type is chosen, then [signIn] for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerTypePickerFlow(
    signingIn: MediaProviderType?,
    onTypeSelected: (MediaProviderType) -> Boolean,
    onDismissRequest: () -> Unit,
    signIn: @Composable (MediaProviderType) -> Unit,
) {
    // The sheet dismisses itself right after every row tap, before [signingIn] recomposes in; an accepted tap must
    // not close the flow. [onTypeSelected] returns whether sign-in opens (false: the paywall does instead).
    var accepted by remember { mutableStateOf(false) }
    if (signingIn == null) {
        ServerTypePickerSheet(
            onTypeSelected = { type -> accepted = onTypeSelected(type) },
            onDismissRequest = { if (!accepted) onDismissRequest() },
        )
    } else {
        signIn(signingIn)
    }
}
