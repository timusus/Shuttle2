package com.simplecityapps.shuttle.ui.screens.sources

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ListItemShapes
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
import com.simplecityapps.shuttle.designsystem.component.SettingProgress
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.model.MediaProviderType
import java.text.NumberFormat
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the Sources cards ask their host to do. */
class SourcesActions(
    val onOpenThisDevice: () -> Unit,
    val onServerClick: (ServerSource) -> Unit,
    val onAddServer: () -> Unit,
)

/**
 * Settings > Sources' own rows (#379, #492), ahead of the catalog's switches: the servers first, each connected one with
 * its account, status, songs and when it last updated (or a sync's progress) and opening its page, and "Add a server"
 * picking a type to sign in to; then This device as one row with its own status, opening its page. [now] is when
 * "Updated 2 hours ago" counts from.
 */
fun LazyListScope.sourcesContent(uiState: SourcesUiState, actions: SourcesActions, now: Instant = Clock.System.now()) {
    item(key = "sources-servers") {
        SettingsGroup(
            title = stringResource(R.string.sources_servers_title),
            rows = uiState.servers.filter { it.connected }.map { server ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(server.type.titleRes),
                        summary = serverStatusLine(server, now),
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
    item(key = "sources-this-device") {
        SettingsGroup(
            rows = listOf(
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(R.string.sources_this_device),
                        summary = when {
                            !uiState.thisDevice -> stringResource(R.string.sources_this_device_off_summary)
                            uiState.folders.hasRevokedFolder -> stringResource(R.string.sources_folder_rules_access_removed)
                            else -> deviceStatusLine(uiState, now)
                        },
                        onClick = actions.onOpenThisDevice,
                        icon = Icons.Rounded.Smartphone,
                        progress = uiState.deviceStatus.progress.takeIf { uiState.thisDevice },
                        shapes = shapes,
                        modifier = Modifier.testTag("sources-this-device"),
                    )
                },
            ),
        )
    }
}

/** This device's status: a scan's progress or failure, else its songs and when it last updated. */
@Composable
internal fun deviceStatusLine(uiState: SourcesUiState, now: Instant): String = when (val status = uiState.deviceStatus) {
    is SourceStatus.Importing -> status.progress?.let { progress ->
        stringResource(R.string.sources_scanning_count, progress.progress.formatted(), progress.total.formatted())
    } ?: stringResource(R.string.sources_scanning)

    is SourceStatus.Failed -> stringResource(R.string.sources_scan_failed, status.error)

    SourceStatus.Idle -> {
        val songs = uiState.deviceSongs?.let { songsLabel(it) }
        val updated = uiState.deviceUpdated?.let { updatedLabel(it, now) }
        when {
            songs != null && updated != null -> stringResource(R.string.sources_status_songs_updated, songs, updated)
            else -> songs ?: updated ?: stringResource(R.string.sources_this_device_summary)
        }
    }
}

/** A server's status: syncing with its progress, unreachable, or its songs, when it last updated and any listing shortfall; led by its "user@host" account once it has one, else "Connected". */
@Composable
internal fun serverStatusLine(server: ServerSource, now: Instant): String = when (val status = server.status) {
    is SourceStatus.Importing -> withAccount(
        server.account,
        status.progress?.let { progress ->
            stringResource(R.string.sources_server_syncing_count, progress.progress.formatted(), progress.total.formatted())
        } ?: stringResource(R.string.sources_server_syncing),
    )

    is SourceStatus.Failed -> withAccount(server.account, stringResource(R.string.sources_server_unreachable))

    SourceStatus.Idle -> {
        val connected = server.account ?: stringResource(R.string.sources_server_connected)
        val withSongs = server.songs?.let { stringResource(R.string.sources_status_songs_updated, connected, songsLabel(it)) } ?: connected
        val withUpdated = server.updated?.let { stringResource(R.string.sources_status_songs_updated, withSongs, updatedLabel(it, now)) } ?: withSongs
        if (server.listingShortfall > 0) {
            stringResource(R.string.sources_status_songs_updated, withUpdated, pluralStringResource(R.plurals.sources_server_listing_shortfall, server.listingShortfall, server.listingShortfall.formatted()))
        } else {
            withUpdated
        }
    }
}

@Composable
private fun withAccount(account: String?, status: String): String = account?.let { stringResource(R.string.sources_status_songs_updated, it, status) } ?: status

@Composable
internal fun songsLabel(count: Int): String = pluralStringResource(R.plurals.sources_songs, count, count.formatted())

@Composable
internal fun updatedLabel(lastImport: Instant, now: Instant): String {
    if (now - lastImport < 1.minutes) return stringResource(R.string.sources_updated_just_now)
    val relative = DateUtils.getRelativeTimeSpanString(lastImport.toEpochMilliseconds(), now.toEpochMilliseconds(), DateUtils.MINUTE_IN_MILLIS)
    return stringResource(R.string.sources_updated, relative.toString().replaceFirstChar { it.lowercase() })
}

internal fun Int.formatted(): String = NumberFormat.getIntegerInstance().format(this)

/** The progress bar a status draws while it's importing: how far through, once the source knows its total. */
internal val SourceStatus.progress: SettingProgress?
    get() = (this as? SourceStatus.Importing)?.let { SettingProgress(it.progress?.asFloat()) }

internal val FolderLists.hasRevokedFolder: Boolean
    get() = (includes + excludes + extras).any { !it.hasAccess }

@get:StringRes
internal val MediaProviderType.titleRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> R.string.media_provider_title_jellyfin
        MediaProviderType.Emby -> R.string.media_provider_title_emby
        MediaProviderType.Plex -> R.string.media_provider_title_plex
        MediaProviderType.Subsonic -> R.string.media_provider_title_subsonic
        MediaProviderType.Shuttle, MediaProviderType.MediaStore -> R.string.sources_this_device
    }
