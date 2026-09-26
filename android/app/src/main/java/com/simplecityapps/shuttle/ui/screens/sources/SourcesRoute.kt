package com.simplecityapps.shuttle.ui.screens.sources

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Wires [sourcesContent] to [SourcesViewModel], the SAF folder picker and the servers' sign-in dialogs, and returns
 * the rows for Settings > Sources to show ahead of its catalog switches.
 */
@Composable
fun sourcesRows(snackbarHostState: SnackbarHostState): LazyListScope.() -> Unit {
    val viewModel: SourcesViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SourcesDialog?>(null) }
    var pickingFolder by rememberSaveable { mutableStateOf<FolderKind?>(null) }
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pickingFolder?.let { kind -> viewModel.onFolderPicked(kind, uri?.toString()) }
        pickingFolder = null
    }
    val launchFolderPicker: (FolderKind) -> Unit = remember(folderPicker) {
        { kind: FolderKind ->
            pickingFolder = kind
            try {
                folderPicker.launch(null)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "No folder picker")
                pickingFolder = null
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.sources_no_folder_picker)) }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SourcesEvent.FolderNotOnDevice -> snackbarHostState.showSnackbar(context.getString(R.string.sources_folder_not_on_device))
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    SourcesDialogHost(
        dialog = dialog,
        onTurnOffThisDevice = { viewModel.onThisDeviceChange(false) },
        onRemoveFolder = viewModel::onRemoveFolder,
        onGrantAccess = launchFolderPicker,
        onSignIn = { signingIn = it },
        onRemoveServer = viewModel::onRemoveServer,
        onDismiss = { dialog = null },
    )
    signingIn?.let { type ->
        ServerSignInRoute(type, onConnected = viewModel::onServerConnected, onDismiss = { signingIn = null })
    }

    val actions = remember(viewModel, launchFolderPicker) {
        SourcesActions(
            onThisDeviceChange = viewModel::onThisDeviceChange,
            onAddFolder = launchFolderPicker,
            onRescan = viewModel::onRescan,
            onServerClick = { server ->
                if (server.connected) {
                    dialog = SourcesDialog.Server(server.type)
                } else if (viewModel.onAddServer()) {
                    signingIn = server.type
                }
            },
            onShowDialog = { dialog = it },
        )
    }
    return { sourcesContent(uiState, actions) }
}
