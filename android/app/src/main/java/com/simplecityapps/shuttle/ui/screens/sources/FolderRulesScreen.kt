package com.simplecityapps.shuttle.ui.screens.sources

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.SettingIconStyle
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold

/** A confirmation Folder rules is asking for. */
sealed interface FolderRulesDialog {
    data class RemoveFolder(val kind: FolderKind, val folder: SourceFolder) : FolderRulesDialog

    /** A folder whose SAF grant was revoked outside the app (#479): grant access again, or remove it. */
    data class RevokedFolder(val kind: FolderKind, val folder: SourceFolder) : FolderRulesDialog
}

/**
 * Settings > Sources > Folder rules (#663): which of this device's folders the S2 scanner includes, leaves out, or
 * reads directly. Tapping a folder asks to remove it, or to fix its access.
 */
@Composable
fun FolderRulesScreen(
    folders: FolderLists,
    onNavigateUp: () -> Unit,
    onAddFolder: (FolderKind) -> Unit,
    onShowDialog: (FolderRulesDialog) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsScaffold(
        title = stringResource(R.string.sources_folder_rules),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        folderGroup(FolderKind.Include, R.string.sources_includes_title, folders.includes, onAddFolder, onShowDialog, emptySummary = R.string.sources_includes_empty)
        folderGroup(FolderKind.Exclude, R.string.sources_excludes_title, folders.excludes, onAddFolder, onShowDialog)
        folderGroup(FolderKind.Extra, R.string.sources_extras_title, folders.extras, onAddFolder, onShowDialog)
    }
}

private fun LazyListScope.folderGroup(
    kind: FolderKind,
    @StringRes title: Int,
    folders: List<SourceFolder>,
    onAddFolder: (FolderKind) -> Unit,
    onShowDialog: (FolderRulesDialog) -> Unit,
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
                            onShowDialog(if (folder.hasAccess) FolderRulesDialog.RemoveFolder(kind, folder) else FolderRulesDialog.RevokedFolder(kind, folder))
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
fun FolderRulesDialogHost(
    dialog: FolderRulesDialog?,
    onRemoveFolder: (FolderKind, SourceFolder) -> Unit,
    onGrantAccess: (FolderKind) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        is FolderRulesDialog.RemoveFolder -> S2Dialog(
            title = stringResource(R.string.sources_remove_folder_title),
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sources_remove),
            onConfirm = {
                onDismiss()
                onRemoveFolder(dialog.kind, dialog.folder)
            },
            dismissLabel = stringResource(android.R.string.cancel),
        ) { Text(dialog.folder.path ?: dialog.folder.name) }

        is FolderRulesDialog.RevokedFolder -> S2Dialog(
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
                Text(stringResource(R.string.sources_folder_access_removed_message, dialog.folder.path ?: dialog.folder.name))
                S2Button(
                    text = stringResource(R.string.sources_grant_access),
                    onClick = {
                        onDismiss()
                        onGrantAccess(dialog.kind)
                    },
                    style = S2ButtonStyle.Text,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
