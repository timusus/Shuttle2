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

/** Type picker then sign-in, never leaving for Settings; dismissal closes the whole flow, not just one step. */
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
