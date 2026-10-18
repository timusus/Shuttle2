package com.simplecityapps.shuttle.ui.screens.sources

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Icon
import com.simplecityapps.shuttle.designsystem.component.SettingProgress
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.TonalContainer
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
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

/** Sources rows ahead of the catalog's switches: servers, then This device; [now] drives the relative "Updated" time. */
fun LazyListScope.sourcesContent(uiState: SourcesUiState, actions: SourcesActions, now: Instant = Clock.System.now()) {
    item(key = "sources-servers") {
        SettingsGroup(
            title = stringResource(R.string.sources_servers_title),
            rows = uiState.servers.filter { it.connected }.map { server ->
                @Composable { shapes: ListItemShapes ->
                    LinkSetting(
                        title = stringResource(server.type.titleRes),
                        summaryHead = server.account ?: stringResource(R.string.sources_server_connected).takeIf { server.status == SourceStatus.Idle },
                        summary = serverSyncLine(server, now),
                        onClick = { actions.onServerClick(server) },
                        leading = { ProviderMark(server.type, failed = server.status is SourceStatus.Failed) },
                        summaryIsError = server.status is SourceStatus.Failed,
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

/** The second line of a server row, under its account: a sync's progress, unreachable, or "N songs · updated X"; null when an idle server has neither yet. */
@Composable
internal fun serverSyncLine(server: ServerSource, now: Instant): String? = when (val status = server.status) {
    is SourceStatus.Importing -> status.progress?.let { progress ->
        stringResource(R.string.sources_server_syncing_count, progress.progress.formatted(), progress.total.formatted())
    } ?: stringResource(R.string.sources_server_syncing)

    is SourceStatus.Failed -> stringResource(R.string.sources_server_unreachable)

    SourceStatus.Idle -> listOfNotNull(
        server.songs?.let { songsLabel(it) },
        server.updated?.let { updatedLabel(it, now) },
        server.listingShortfall.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.sources_server_listing_shortfall, it, it.formatted()) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** [serverSyncLine] on one line, led by the server's "user@host" account once it has one, else "Connected" while idle. */
@Composable
internal fun serverStatusLine(server: ServerSource, now: Instant): String {
    val lead = server.account ?: stringResource(R.string.sources_server_connected).takeIf { server.status == SourceStatus.Idle }
    return listOfNotNull(lead, serverSyncLine(server, now)).joinToString(" · ")
}

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

@get:StringRes
internal val MediaProviderType.descriptionRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> MediaProviderR.string.media_provider_description_jellyfin
        MediaProviderType.Emby -> MediaProviderR.string.media_provider_description_emby
        MediaProviderType.Plex -> MediaProviderR.string.media_provider_description_plex
        MediaProviderType.Subsonic -> MediaProviderR.string.media_provider_description_subsonic
        MediaProviderType.Shuttle -> MediaProviderR.string.media_provider_description_s2
        MediaProviderType.MediaStore -> MediaProviderR.string.media_provider_description_media_store
    }

@get:DrawableRes
private val MediaProviderType.markRes: Int
    get() = when (this) {
        MediaProviderType.Jellyfin -> MediaProviderR.drawable.ic_jellyfin
        MediaProviderType.Emby -> MediaProviderR.drawable.ic_emby
        MediaProviderType.Plex -> MediaProviderR.drawable.ic_plex
        MediaProviderType.Subsonic -> MediaProviderR.drawable.ic_subsonic
        MediaProviderType.Shuttle -> MediaProviderR.drawable.ic_s2
        MediaProviderType.MediaStore -> MediaProviderR.drawable.ic_baseline_android_24
    }

/** The provider's own mark in the same tonal square as the other settings icons, as the servers' row icon and in the type picker; [failed] adds an error badge. */
@Composable
internal fun ProviderMark(type: MediaProviderType, modifier: Modifier = Modifier, failed: Boolean = false) {
    Box(modifier) {
        TonalContainer(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, size = S2IconSize.container) {
            Image(painterResource(type.markRes), contentDescription = null, modifier = Modifier.size(S2IconSize.medium))
        }
        if (failed) {
            S2Icon(
                Icons.Rounded.Error,
                contentDescription = null, // the row summary already says "Can't reach"
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(S2IconSize.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .testTag("server-error-badge"),
            )
        }
    }
}
