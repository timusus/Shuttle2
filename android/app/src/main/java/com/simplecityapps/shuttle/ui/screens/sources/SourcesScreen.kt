package com.simplecityapps.shuttle.ui.screens.sources

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.model.MediaProviderType

/** A confirmation Sources is asking for. */
sealed interface SourcesDialog {
    data object TurnOffThisDevice : SourcesDialog

    data class RemoveFolder(val kind: FolderKind, val folder: SourceFolder) : SourcesDialog

    /** A connected server's options: sign in again, or remove it. */
    data class Server(val type: MediaProviderType) : SourcesDialog
}

/** What the Sources rows ask their host to do. */
class SourcesActions(
    val onThisDeviceChange: (Boolean) -> Unit,
    val onAddFolder: (FolderKind) -> Unit,
    val onRescan: () -> Unit,
    val onServerClick: (ServerSource) -> Unit,
    val onShowDialog: (SourcesDialog) -> Unit,
)

/**
 * Settings > Sources' own rows (#379), ahead of the catalog's switches: this device, the S2 scanner's folders, a
 * rescan with its progress, and one row per media server.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.sourcesContent(uiState: SourcesUiState, actions: SourcesActions) {
    item(key = "sources-this-device") {
        SettingsGroup(
            rows = listOf { shapes ->
                SwitchSetting(
                    title = stringResource(R.string.sources_this_device),
                    summary = stringResource(if (uiState.usesAndroidProvider) R.string.sources_android_provider_summary else R.string.sources_this_device_summary),
                    checked = uiState.thisDevice,
                    onCheckedChange = { checked -> if (checked) actions.onThisDeviceChange(true) else actions.onShowDialog(SourcesDialog.TurnOffThisDevice) },
                    icon = Icons.Rounded.Smartphone,
                    shapes = shapes,
                    modifier = Modifier.testTag("sources-this-device"),
                )
            },
        )
    }
    if (uiState.thisDevice) {
        folderGroup(FolderKind.Include, R.string.sources_includes_title, uiState.folders.includes, actions, emptySummary = R.string.sources_includes_empty)
        folderGroup(FolderKind.Exclude, R.string.sources_excludes_title, uiState.folders.excludes, actions)
        folderGroup(FolderKind.Extra, R.string.sources_extras_title, uiState.folders.extras, actions, emptySummary = R.string.sources_extras_summary)
    }
    item(key = "sources-rescan") {
        Column {
            SettingsGroup(
                rows = listOf { shapes ->
                    LinkSetting(
                        title = stringResource(R.string.sources_scan_now),
                        summary = when {
                            uiState.scan != null -> uiState.scan.message ?: stringResource(R.string.sources_scanning_title)
                            uiState.scanError != null -> stringResource(R.string.sources_scan_failed, uiState.scanError)
                            else -> stringResource(R.string.sources_scan_summary)
                        },
                        onClick = actions.onRescan,
                        enabled = uiState.scan == null,
                        icon = Icons.Rounded.Refresh,
                        shapes = shapes,
                        modifier = Modifier.testTag("sources-rescan"),
                    )
                },
            )
            uiState.scan?.let { scan ->
                val modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("sources-scan-progress")
                if (scan.fraction != null) LinearWavyProgressIndicator(progress = { scan.fraction }, modifier = modifier) else LinearWavyProgressIndicator(modifier = modifier)
            }
        }
    }
    item(key = "sources-servers") {
        SettingsGroup(
            title = stringResource(R.string.sources_servers_title),
            rows = uiState.servers.map { server ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(server.type.titleRes),
                        summary = stringResource(if (server.connected) R.string.sources_server_connected else R.string.sources_server_not_connected),
                        onClick = { actions.onServerClick(server) },
                        icon = Icons.Rounded.Dns,
                        shapes = shapes,
                        modifier = Modifier.testTag("sources-server-${server.type.name}"),
                    )
                }
            },
        )
    }
}

private fun LazyListScope.folderGroup(
    kind: FolderKind,
    @StringRes title: Int,
    folders: List<SourceFolder>,
    actions: SourcesActions,
    @StringRes emptySummary: Int? = null,
) {
    item(key = "sources-folders-${kind.name}") {
        SettingsGroup(
            title = stringResource(title),
            rows = folders.map { folder ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = folder.name,
                        summary = folder.path,
                        onClick = { actions.onShowDialog(SourcesDialog.RemoveFolder(kind, folder)) },
                        icon = Icons.Rounded.Folder,
                        shapes = shapes,
                    )
                }
            } + @Composable { shapes: ListItemShapes ->
                LinkSetting(
                    title = stringResource(R.string.sources_add_folder),
                    summary = emptySummary?.takeIf { folders.isEmpty() }?.let { stringResource(it) },
                    onClick = { actions.onAddFolder(kind) },
                    icon = Icons.Rounded.Add,
                    shapes = shapes,
                    modifier = Modifier.testTag("sources-add-folder-${kind.name}"),
                )
            },
        )
    }
}

/** The confirmation [dialog] asks for; [onConfirm] runs the action, and each option closes it through [onDismiss]. */
@Composable
fun SourcesDialogHost(
    dialog: SourcesDialog?,
    onTurnOffThisDevice: () -> Unit,
    onRemoveFolder: (FolderKind, SourceFolder) -> Unit,
    onSignIn: (MediaProviderType) -> Unit,
    onRemoveServer: (MediaProviderType) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        SourcesDialog.TurnOffThisDevice -> S2Dialog(
            title = stringResource(R.string.sources_this_device_off_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_turn_off),
            onConfirm = {
                onDismiss()
                onTurnOffThisDevice()
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = true,
        ) { Text(stringResource(R.string.sources_this_device_off_message)) }

        is SourcesDialog.RemoveFolder -> S2Dialog(
            title = stringResource(R.string.sources_remove_folder_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_remove),
            onConfirm = {
                onDismiss()
                onRemoveFolder(dialog.kind, dialog.folder)
            },
            dismissLabel = stringResource(android.R.string.cancel),
        ) { Text(dialog.folder.path ?: dialog.folder.name) }

        is SourcesDialog.Server -> S2Dialog(
            title = stringResource(R.string.sources_remove_server_title, stringResource(dialog.type.titleRes)),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_remove),
            onConfirm = {
                onDismiss()
                onRemoveServer(dialog.type)
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = true,
        ) {
            Column {
                Text(stringResource(R.string.sources_remove_server_message))
                S2Button(
                    text = stringResource(R.string.sources_server_sign_in),
                    onClick = {
                        onDismiss()
                        onSignIn(dialog.type)
                    },
                    style = S2ButtonStyle.Text,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@get:StringRes
private val MediaProviderType.titleRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> R.string.media_provider_title_jellyfin
        MediaProviderType.Emby -> R.string.media_provider_title_emby
        MediaProviderType.Plex -> R.string.media_provider_title_plex
        MediaProviderType.Shuttle, MediaProviderType.MediaStore -> R.string.sources_this_device
    }
