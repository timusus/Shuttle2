package com.simplecityapps.shuttle.ui.screens.sources

import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.servers.SERVER_CONNECTED_REQUEST
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute
import com.simplecityapps.shuttle.ui.screens.sources.servers.connectedServerType
import com.simplecityapps.shuttle.ui.screens.sources.servers.showServerSignIn
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
    val fragmentManager = (LocalActivity.current as? FragmentActivity)?.supportFragmentManager
    val lifecycleOwner = LocalLifecycleOwner.current
    var dialog by remember { mutableStateOf<SourcesDialog?>(null) }
    var pickingFolder by rememberSaveable { mutableStateOf<FolderKind?>(null) }
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }
    val signIn: (MediaProviderType) -> Unit = { type ->
        if (type == MediaProviderType.Plex) signingIn = type else fragmentManager?.showServerSignIn(type)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pickingFolder?.let { kind -> viewModel.onFolderPicked(kind, uri?.toString()) }
        pickingFolder = null
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
    DisposableEffect(fragmentManager, lifecycleOwner) {
        fragmentManager?.setFragmentResultListener(SERVER_CONNECTED_REQUEST, lifecycleOwner) { _, result ->
            result.connectedServerType()?.let(viewModel::onServerConnected)
        }
        onDispose { fragmentManager?.clearFragmentResultListener(SERVER_CONNECTED_REQUEST) }
    }

    SourcesDialogHost(
        dialog = dialog,
        onTurnOffThisDevice = { viewModel.onThisDeviceChange(false) },
        onRemoveFolder = viewModel::onRemoveFolder,
        onSignIn = signIn,
        onRemoveServer = viewModel::onRemoveServer,
        onDismiss = { dialog = null },
    )
    signingIn?.let { type ->
        ServerSignInRoute(type, onConnected = viewModel::onServerConnected, onDismiss = { signingIn = null })
    }

    val actions = remember(viewModel, fragmentManager, folderPicker) {
        SourcesActions(
            onThisDeviceChange = viewModel::onThisDeviceChange,
            onAddFolder = { kind ->
                pickingFolder = kind
                try {
                    folderPicker.launch(null)
                } catch (e: ActivityNotFoundException) {
                    Timber.e(e, "No folder picker")
                    pickingFolder = null
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.sources_no_folder_picker)) }
                }
            },
            onRescan = viewModel::onRescan,
            onServerClick = { server ->
                if (server.connected) {
                    dialog = SourcesDialog.Server(server.type)
                } else if (viewModel.onAddServer()) {
                    signIn(server.type)
                }
            },
            onShowDialog = { dialog = it },
        )
    }
    return { sourcesContent(uiState, actions) }
}
