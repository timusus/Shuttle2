package com.simplecityapps.shuttle.ui.screens.sources

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SettingIconStyle
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold
import kotlin.time.Clock
import kotlin.time.Instant

/** A confirmation This device is asking for. */
sealed interface ThisDeviceDialog {
    data object TurnOff : ThisDeviceDialog

    data class RemoveFolder(val kind: FolderKind, val folder: SourceFolder) : ThisDeviceDialog

    /** A folder whose SAF grant was revoked outside the app (#479): grant access again, or remove it. */
    data class RevokedFolder(val kind: FolderKind, val folder: SourceFolder) : ThisDeviceDialog
}

/** What This device asks its host to do. */
class ThisDeviceActions(
    val onThisDeviceChange: (Boolean) -> Unit,
    val onRescan: () -> Unit,
    /** Reads the files this device's import couldn't read again (#840). */
    val onRetrySkippedFiles: () -> Unit,
    val onAddFolder: (FolderKind) -> Unit,
    val onShowDialog: (ThisDeviceDialog) -> Unit,
)

/**
 * Settings > Sources > This device (#492): its switch with the import's status, a Scan now button, any files the last
 * scan couldn't read with a retry, and the folder rules (scan only these folders, exclude folders, extra folders).
 * Tapping a folder asks to remove it, or to fix its access. [now] is when "Updated 2 hours ago" counts from.
 */
@Composable
fun ThisDeviceScreen(
    uiState: SourcesUiState,
    folders: FolderLists,
    actions: ThisDeviceActions,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    now: Instant = Clock.System.now(),
) {
    SettingsScaffold(
        title = stringResource(R.string.sources_this_device),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item(key = "this-device-status") {
            val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>(
                { shapes ->
                    SwitchSetting(
                        title = stringResource(R.string.sources_this_device),
                        summary = if (uiState.thisDevice) deviceStatusLine(uiState, now) else stringResource(R.string.sources_this_device_summary),
                        checked = uiState.thisDevice,
                        onCheckedChange = { checked -> if (checked) actions.onThisDeviceChange(true) else actions.onShowDialog(ThisDeviceDialog.TurnOff) },
                        icon = Icons.Rounded.Smartphone,
                        progress = uiState.deviceStatus.progress.takeIf { uiState.thisDevice },
                        shapes = shapes,
                        modifier = Modifier.testTag("sources-this-device"),
                    )
                },
            )
            if (uiState.thisDevice) {
                rows += { shapes ->
                    ActionsSetting(shapes = shapes) {
                        S2Button(
                            text = stringResource(R.string.sources_scan_now),
                            onClick = actions.onRescan,
                            style = S2ButtonStyle.Tonal,
                            icon = Icons.Rounded.Refresh,
                            enabled = uiState.deviceStatus !is SourceStatus.Importing,
                            modifier = Modifier.testTag("sources-rescan"),
                        )
                    }
                }
                if (uiState.deviceSkippedFiles > 0) {
                    rows += { shapes ->
                        ActionsSetting(
                            summary = pluralStringResource(R.plurals.sources_skipped_files, uiState.deviceSkippedFiles, uiState.deviceSkippedFiles.formatted()),
                            shapes = shapes,
                            modifier = Modifier.testTag("sources-skipped-files"),
                        ) {
                            S2Button(
                                text = stringResource(R.string.sources_skipped_files_retry),
                                onClick = actions.onRetrySkippedFiles,
                                style = S2ButtonStyle.Tonal,
                                enabled = uiState.deviceStatus !is SourceStatus.Importing,
                                modifier = Modifier.testTag("sources-retry-skipped-files"),
                            )
                        }
                    }
                }
            }
            SettingsGroup(rows = rows)
        }
        if (uiState.thisDevice) {
            if (uiState.usesAndroidProvider) {
                item(key = "this-device-android-provider") {
                    S2Text(
                        text = stringResource(R.string.sources_folder_rules_android_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = S2Spacing.medium).testTag("sources-folder-rules-unavailable"),
                    )
                }
            } else {
                folderGroup(FolderKind.Include, R.string.sources_includes_title, folders.includes, actions.onAddFolder, actions.onShowDialog, emptySummary = R.string.sources_includes_empty)
                folderGroup(FolderKind.Exclude, R.string.sources_excludes_title, folders.excludes, actions.onAddFolder, actions.onShowDialog)
                folderGroup(FolderKind.Extra, R.string.sources_extras_title, folders.extras, actions.onAddFolder, actions.onShowDialog, emptySummary = R.string.sources_extras_summary)
            }
        }
    }
}

private fun LazyListScope.folderGroup(
    kind: FolderKind,
    @StringRes title: Int,
    folders: List<SourceFolder>,
    onAddFolder: (FolderKind) -> Unit,
    onShowDialog: (ThisDeviceDialog) -> Unit,
    @StringRes emptySummary: Int? = null,
) {
    item(key = "folder-rules-${kind.name}") {
        SettingsGroup(
            title = stringResource(title),
            rows = folders.map { folder ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = folder.name,
                        summary = if (folder.hasAccess) folder.path else stringResource(R.string.sources_folder_access_removed_summary),
                        onClick = {
                            onShowDialog(if (folder.hasAccess) ThisDeviceDialog.RemoveFolder(kind, folder) else ThisDeviceDialog.RevokedFolder(kind, folder))
                        },
                        icon = if (folder.hasAccess) Icons.Rounded.Folder else Icons.Rounded.FolderOff,
                        iconStyle = SettingIconStyle.Plain,
                        shapes = shapes,
                    )
                }
            } + @Composable { shapes: ListItemShapes ->
                ActionsSetting(
                    summary = emptySummary?.takeIf { folders.isEmpty() }?.let { stringResource(it) },
                    shapes = shapes,
                ) {
                    S2Button(
                        text = stringResource(R.string.sources_add_folder),
                        onClick = { onAddFolder(kind) },
                        style = S2ButtonStyle.Text,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("folder-rules-add-${kind.name}"),
                    )
                }
            },
        )
    }
}

/** The confirmation [dialog] asks for; each option closes it through [onDismiss]. */
@Composable
fun ThisDeviceDialogHost(
    dialog: ThisDeviceDialog?,
    onTurnOffThisDevice: () -> Unit,
    onRemoveFolder: (FolderKind, SourceFolder) -> Unit,
    onGrantAccess: (FolderKind) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        ThisDeviceDialog.TurnOff -> S2Dialog(
            title = stringResource(R.string.sources_this_device_off_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_turn_off),
            onConfirm = {
                onDismiss()
                onTurnOffThisDevice()
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = true,
        ) { S2Text(stringResource(R.string.sources_this_device_off_message)) }

        is ThisDeviceDialog.RemoveFolder -> S2Dialog(
            title = stringResource(R.string.sources_remove_folder_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_remove),
            onConfirm = {
                onDismiss()
                onRemoveFolder(dialog.kind, dialog.folder)
            },
            dismissLabel = stringResource(android.R.string.cancel),
        ) { S2Text(dialog.folder.path ?: dialog.folder.name) }

        is ThisDeviceDialog.RevokedFolder -> S2Dialog(
            title = stringResource(R.string.sources_folder_access_removed_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_remove),
            onConfirm = {
                onDismiss()
                onRemoveFolder(dialog.kind, dialog.folder)
            },
            dismissLabel = stringResource(android.R.string.cancel),
            destructive = true,
        ) {
            Column {
                S2Text(stringResource(R.string.sources_folder_access_removed_message, dialog.folder.path ?: dialog.folder.name))
                S2Button(
                    text = stringResource(R.string.sources_grant_access),
                    onClick = {
                        onDismiss()
                        onGrantAccess(dialog.kind)
                    },
                    style = S2ButtonStyle.Text,
                    modifier = Modifier.padding(top = S2Spacing.small),
                )
            }
        }
    }
}
