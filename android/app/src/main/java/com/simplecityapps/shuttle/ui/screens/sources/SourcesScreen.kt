package com.simplecityapps.shuttle.ui.screens.sources

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.simplecityapps.shuttle.designsystem.component.SettingProgress
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.MediaProviderType
import java.text.NumberFormat
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A confirmation Sources is asking for. */
sealed interface SourcesDialog {
    data object TurnOffThisDevice : SourcesDialog

    /** A connected server's options: sign in again, or remove it. */
    data class Server(val type: MediaProviderType) : SourcesDialog
}

/** What the Sources cards ask their host to do. */
class SourcesActions(
    val onThisDeviceChange: (Boolean) -> Unit,
    val onRescan: () -> Unit,
    val onOpenFolderRules: () -> Unit,
    val onServerClick: (ServerSource) -> Unit,
    val onAddServer: () -> Unit,
    val onShowDialog: (SourcesDialog) -> Unit,
)

/**
 * Settings > Sources' own rows (#379), ahead of the catalog's switches: a card per source with its status (#663). This
 * device has its switch, its songs and when the library last updated (or a scan's progress), its folder rules and a
 * rescan; each connected server has its status and songs, and opens its options; "Add a server" picks a type to sign
 * in to. [now] is when "Updated 2 hours ago" counts from.
 */
fun LazyListScope.sourcesContent(uiState: SourcesUiState, actions: SourcesActions, now: Instant = Clock.System.now()) {
    item(key = "sources-this-device") {
        val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>(
            { shapes ->
                SwitchSetting(
                    title = stringResource(R.string.sources_this_device),
                    summary = if (uiState.thisDevice) deviceStatusLine(uiState, now) else stringResource(R.string.sources_this_device_summary),
                    checked = uiState.thisDevice,
                    onCheckedChange = { checked -> if (checked) actions.onThisDeviceChange(true) else actions.onShowDialog(SourcesDialog.TurnOffThisDevice) },
                    icon = Icons.Rounded.Smartphone,
                    progress = uiState.deviceStatus.progress.takeIf { uiState.thisDevice },
                    shapes = shapes,
                    modifier = Modifier.testTag("sources-this-device"),
                )
            },
        )
        if (uiState.thisDevice) {
            rows += { shapes ->
                LinkSetting(
                    title = stringResource(R.string.sources_folder_rules),
                    summary = stringResource(
                        when {
                            uiState.usesAndroidProvider -> R.string.sources_folder_rules_android_summary
                            uiState.folders.hasRevokedFolder -> R.string.sources_folder_rules_access_removed
                            else -> R.string.sources_folder_rules_summary
                        },
                    ),
                    onClick = actions.onOpenFolderRules,
                    icon = Icons.Rounded.FolderOpen,
                    shapes = shapes,
                    modifier = Modifier.testTag("sources-folder-rules"),
                )
            }
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
        }
        SettingsGroup(rows = rows)
    }
    item(key = "sources-servers") {
        SettingsGroup(
            title = stringResource(R.string.sources_servers_title),
            rows = uiState.servers.filter { it.connected }.map { server ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(server.type.titleRes),
                        summary = serverStatusLine(server),
                        onClick = { actions.onServerClick(server) },
                        icon = if (server.status is SourceStatus.Failed) Icons.Rounded.CloudOff else Icons.Rounded.Dns,
                        progress = server.status.progress,
                        shapes = shapes,
                        modifier = Modifier.testTag("sources-server-${server.type.name}"),
                    )
                }
            } + @Composable { shapes: ListItemShapes ->
                ActionsSetting(shapes = shapes) {
                    S2Button(
                        text = stringResource(R.string.sources_add_server),
                        onClick = actions.onAddServer,
                        style = S2ButtonStyle.Tonal,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("sources-add-server"),
                    )
                }
            },
        )
    }
}

/** This device's status: a scan's progress or failure, else its songs and when the library last updated. */
@Composable
private fun deviceStatusLine(uiState: SourcesUiState, now: Instant): String = when (val status = uiState.deviceStatus) {
    is SourceStatus.Importing -> status.progress?.let { progress ->
        stringResource(R.string.sources_scanning_count, progress.progress.formatted(), progress.total.formatted())
    } ?: stringResource(R.string.sources_scanning)

    is SourceStatus.Failed -> stringResource(R.string.sources_scan_failed, status.error)

    SourceStatus.Idle -> {
        val songs = uiState.deviceSongs?.let { songsLabel(it) }
        val updated = uiState.lastImport?.let { updatedLabel(it, now) }
        when {
            songs != null && updated != null -> stringResource(R.string.sources_status_songs_updated, songs, updated)
            else -> songs ?: updated ?: stringResource(R.string.sources_this_device_summary)
        }
    }
}

/** A server's status: syncing with its progress, unreachable, or connected with its songs. */
@Composable
private fun serverStatusLine(server: ServerSource): String = when (val status = server.status) {
    is SourceStatus.Importing -> status.progress?.let { progress ->
        stringResource(R.string.sources_server_syncing_count, progress.progress.formatted(), progress.total.formatted())
    } ?: stringResource(R.string.sources_server_syncing)

    is SourceStatus.Failed -> stringResource(R.string.sources_server_unreachable)

    SourceStatus.Idle -> {
        val connected = stringResource(R.string.sources_server_connected)
        server.songs?.let { stringResource(R.string.sources_status_songs_updated, connected, songsLabel(it)) } ?: connected
    }
}

@Composable
private fun songsLabel(count: Int): String = pluralStringResource(R.plurals.sources_songs, count, count.formatted())

@Composable
private fun updatedLabel(lastImport: Instant, now: Instant): String {
    if (now - lastImport < 1.minutes) return stringResource(R.string.sources_updated_just_now)
    val relative = DateUtils.getRelativeTimeSpanString(lastImport.toEpochMilliseconds(), now.toEpochMilliseconds(), DateUtils.MINUTE_IN_MILLIS)
    return stringResource(R.string.sources_updated, relative.toString().replaceFirstChar { it.lowercase() })
}

private fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)

/** The progress bar a status draws while it's importing: how far through, once the source knows its total. */
private val SourceStatus.progress: SettingProgress?
    get() = (this as? SourceStatus.Importing)?.let { SettingProgress(it.progress?.asFloat()) }

private val FolderLists.hasRevokedFolder: Boolean
    get() = (includes + excludes + extras).any { !it.hasAccess }

/** The confirmation [dialog] asks for; [onConfirm] runs the action, and each option closes it through [onDismiss]. */
@Composable
fun SourcesDialogHost(
    dialog: SourcesDialog?,
    onTurnOffThisDevice: () -> Unit,
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
                    modifier = Modifier.padding(top = S2Spacing.small),
                )
            }
        }
    }
}

@get:StringRes
internal val MediaProviderType.titleRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> R.string.media_provider_title_jellyfin
        MediaProviderType.Emby -> R.string.media_provider_title_emby
        MediaProviderType.Plex -> R.string.media_provider_title_plex
        MediaProviderType.Shuttle, MediaProviderType.MediaStore -> R.string.sources_this_device
    }
