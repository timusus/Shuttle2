package com.simplecityapps.shuttle.ui.screens.sources

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Wires [sourcesContent] to [SourcesViewModel], the server type picker and the servers' sign-in dialogs, and returns
 * the cards for Settings > Sources to show ahead of its catalog switches. [onOpenThisDevice] opens [ThisDeviceEntry] and
 * [onOpenServer] a server's [ServerDetailEntry].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun sourcesRows(onOpenThisDevice: () -> Unit, onOpenServer: (MediaProviderType) -> Unit): LazyListScope.() -> Unit {
    val viewModel: SourcesViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingServer by rememberSaveable { mutableStateOf(false) }
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }

    // Refreshes the folders' access, which the This device row flags
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    if (pickingServer) {
        ServerTypePickerSheet(
            onTypeSelected = { type ->
                pickingServer = false
                if (viewModel.onAddServer()) signingIn = type
            },
            onDismissRequest = { pickingServer = false },
        )
    }
    signingIn?.let { type ->
        ServerSignInRoute(type, onConnected = viewModel::onServerConnected, onDismiss = { signingIn = null })
    }

    val actions = remember(onOpenThisDevice, onOpenServer) {
        SourcesActions(
            onOpenThisDevice = onOpenThisDevice,
            onServerClick = { server -> onOpenServer(server.type) },
            onAddServer = { pickingServer = true },
        )
    }
    return { sourcesContent(uiState, actions) }
}

/** Settings > Sources > a server: wires [ServerDetailScreen] to [SourcesViewModel] and the server's sign-in dialog. */
@Composable
fun ServerDetailEntry(typeName: String, onNavigateUp: () -> Unit) {
    val type = MediaProviderType.entries.firstOrNull { it.name == typeName } ?: return
    val viewModel: SourcesViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    var signingIn by rememberSaveable { mutableStateOf(false) }
    // Removing the server leaves, and so does the page once it sees the server gone: leave once
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            onNavigateUp()
        }
    }

    ServerDetailDialogHost(
        type = type,
        visible = confirmingRemove,
        onRemoveServer = {
            viewModel.onRemoveServer(type)
            leave()
        },
        onDismiss = { confirmingRemove = false },
    )
    if (signingIn) ServerSignInRoute(type, onConnected = viewModel::onServerConnected, onDismiss = { signingIn = false })
    ServerDetailScreen(
        type = type,
        server = uiState.servers.firstOrNull { it.type == type },
        actions = ServerDetailActions(
            onSync = viewModel::onRescan,
            onSignIn = { signingIn = true },
            onRemove = { confirmingRemove = true },
        ),
        onNavigateUp = leave,
    )
}

/** Settings > Sources > This device: wires [ThisDeviceScreen] to [SourcesViewModel], [FolderRulesViewModel] and the SAF folder picker. */
@Composable
fun ThisDeviceEntry(onNavigateUp: () -> Unit) {
    val sources: SourcesViewModel = metroViewModel()
    val folderRules: FolderRulesViewModel = metroViewModel()
    val sourcesState by sources.uiState.collectAsStateWithLifecycle()
    val folderRulesState by folderRules.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<ThisDeviceDialog?>(null) }
    var pickingFolder by rememberSaveable { mutableStateOf<FolderKind?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pickingFolder?.let { kind -> folderRules.onFolderPicked(kind, uri?.toString()) }
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
    ConsumeEvents(folderRulesState.events, folderRules::onEventHandled) { event ->
        when (event) {
            FolderRulesEvent.FolderNotOnDevice -> snackbarHostState.showSnackbar(context.getString(R.string.sources_folder_not_on_device))
        }
    }
    LifecycleResumeEffect(folderRules) {
        folderRules.onResume()
        onPauseOrDispose {}
    }

    ThisDeviceDialogHost(
        dialog = dialog,
        onTurnOffThisDevice = { sources.onThisDeviceChange(false) },
        onRemoveFolder = folderRules::onRemoveFolder,
        onGrantAccess = launchFolderPicker,
        onDismiss = { dialog = null },
    )
    val actions = remember(sources, launchFolderPicker) {
        ThisDeviceActions(
            onThisDeviceChange = sources::onThisDeviceChange,
            onRescan = sources::onRescan,
            onRetrySkippedFiles = sources::onRetrySkippedFiles,
            onAddFolder = launchFolderPicker,
            onShowDialog = { dialog = it },
        )
    }
    ThisDeviceScreen(
        uiState = sourcesState,
        folders = folderRulesState.folders,
        actions = actions,
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
    )
}
