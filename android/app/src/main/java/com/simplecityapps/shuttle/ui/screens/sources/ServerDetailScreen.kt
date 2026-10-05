package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSetting
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold
import kotlin.time.Clock
import kotlin.time.Instant

/** What a server's page asks its host to do. */
class ServerDetailActions(
    val onSync: () -> Unit,
    val onSignIn: () -> Unit,
    /** Asks to remove the server; [ServerDetailDialogHost] confirms. */
    val onRemove: () -> Unit,
)

/**
 * Settings > Sources > a server (#492): its status and what it holds, its account (to sign in again), a Sync now button
 * and Remove. Streaming quality and downloads live under Settings > Sources' own rows rather than here. [now] is when
 * "Updated 2 hours ago" counts from.
 */
@Composable
fun ServerDetailScreen(
    type: MediaProviderType,
    server: ServerSource?,
    actions: ServerDetailActions,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
) {
    // A server that disconnects or is removed while its page is open leaves nothing to show, so the page closes. One not
    // yet connected (the sources still loading) keeps it open.
    val connected = server?.connected == true
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(connected) {
        if (connected) {
            wasConnected = true
        } else if (wasConnected) {
            onNavigateUp()
        }
    }
    SettingsScaffold(
        title = stringResource(type.titleRes),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    ) {
        if (server == null || !server.connected) return@SettingsScaffold
        item(key = "server-status") {
            SettingsGroup(
                rows = listOf(
                    @Composable { shapes: ListItemShapes ->
                        InfoSetting(
                            title = stringResource(R.string.sources_server_status),
                            summary = serverStatusLine(server.copy(account = null), now),
                            icon = if (server.status is SourceStatus.Failed) Icons.Rounded.CloudOff else Icons.Rounded.Dns,
                            shapes = shapes,
                            modifier = Modifier.testTag("server-detail-status"),
                        )
                    },
                    @Composable { shapes: ListItemShapes ->
                        ActionsSetting(shapes = shapes) {
                            S2Button(
                                text = stringResource(R.string.sources_server_sync_now),
                                onClick = actions.onSync,
                                style = S2ButtonStyle.Tonal,
                                icon = Icons.Rounded.Refresh,
                                enabled = server.status !is SourceStatus.Importing,
                                modifier = Modifier.testTag("server-detail-sync"),
                            )
                        }
                    },
                ),
            )
        }
        item(key = "server-account") {
            SettingsGroup(
                title = stringResource(R.string.sources_server_account),
                rows = listOf(
                    @Composable { shapes: ListItemShapes ->
                        LinkSetting(
                            title = server.account ?: stringResource(R.string.sources_server_connected),
                            summary = stringResource(R.string.sources_server_sign_in),
                            onClick = actions.onSignIn,
                            icon = Icons.Rounded.AccountCircle,
                            shapes = shapes,
                            modifier = Modifier.testTag("server-detail-account"),
                        )
                    },
                ),
            )
        }
        item(key = "server-remove") {
            SettingsGroup(
                rows = listOf(
                    @Composable { shapes: ListItemShapes ->
                        ActionsSetting(summary = stringResource(R.string.sources_server_remove_summary), shapes = shapes) {
                            S2Button(
                                text = stringResource(R.string.sources_remove),
                                onClick = actions.onRemove,
                                style = S2ButtonStyle.Text,
                                modifier = Modifier.testTag("server-detail-remove"),
                            )
                        }
                    },
                ),
            )
        }
    }
}

/** The confirmation to remove [type]'s server, shown while [visible]. */
@Composable
fun ServerDetailDialogHost(
    type: MediaProviderType,
    visible: Boolean,
    onRemoveServer: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    S2Dialog(
        title = stringResource(R.string.sources_remove_server_title, stringResource(type.titleRes)),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.sources_remove),
        onConfirm = {
            onDismiss()
            onRemoveServer()
        },
        dismissLabel = stringResource(android.R.string.cancel),
        destructive = true,
    ) { Text(stringResource(R.string.sources_remove_server_message)) }
}
