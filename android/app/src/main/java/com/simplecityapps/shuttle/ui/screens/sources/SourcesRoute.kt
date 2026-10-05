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
 * the cards for Settings > Sources to show ahead of its catalog switches. [onOpenFolderRules] opens [FolderRulesEntry].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun sourcesRows(onOpenFolderRules: () -> Unit): LazyListScope.() -> Unit {
    val viewModel: SourcesViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SourcesDialog?>(null) }
    var pickingServer by rememberSaveable { mutableStateOf(false) }
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }

    // Refreshes the folders' access, which the folder rules row flags
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    SourcesDialogHost(
        dialog = dialog,
        onTurnOffThisDevice = { viewModel.onThisDeviceChange(false) },
        onSignIn = { signingIn = it },
        onRemoveServer = viewModel::onRemoveServer,
        onDismiss = { dialog = null },
    )
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

    val actions = remember(viewModel, onOpenFolderRules) {
        SourcesActions(
            onThisDeviceChange = viewModel::onThisDeviceChange,
            onRescan = viewModel::onRescan,
            onRetrySkippedFiles = viewModel::onRetrySkippedFiles,
            onOpenFolderRules = onOpenFolderRules,
            onServerClick = { server -> dialog = SourcesDialog.Server(server.type) },
            onAddServer = { pickingServer = true },
            onShowDialog = { dialog = it },
        )
    }
    return { sourcesContent(uiState, actions) }
}

/** Settings > Sources > Folder rules: wires [FolderRulesScreen] to [FolderRulesViewModel] and the SAF folder picker. */
@Composable
fun FolderRulesEntry(onNavigateUp: () -> Unit) {
    val viewModel: FolderRulesViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<FolderRulesDialog?>(null) }
    var pickingFolder by rememberSaveable { mutableStateOf<FolderKind?>(null) }

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
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            FolderRulesEvent.FolderNotOnDevice -> snackbarHostState.showSnackbar(context.getString(R.string.sources_folder_not_on_device))
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    FolderRulesDialogHost(
        dialog = dialog,
        onRemoveFolder = viewModel::onRemoveFolder,
        onGrantAccess = launchFolderPicker,
        onDismiss = { dialog = null },
    )
    FolderRulesScreen(
        folders = uiState.folders,
        onNavigateUp = onNavigateUp,
        onAddFolder = launchFolderPicker,
        onShowDialog = { dialog = it },
        snackbarHostState = snackbarHostState,
    )
}
