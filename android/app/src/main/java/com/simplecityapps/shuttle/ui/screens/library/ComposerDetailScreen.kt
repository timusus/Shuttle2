package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Icon
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.heroTitle
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle
import com.simplecityapps.shuttle.designsystem.theme.rowTitle
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.ComposerWork
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.composer.ComposerUiState
import com.simplecityapps.shuttle.ui.screens.library.composer.ComposerViewModel
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** A composer's page: their classical works (one row per work, across every recording), then the albums holding them. */
@Composable
fun ComposerDetailScreen(
    uiState: ComposerUiState,
    onNavigateUp: () -> Unit,
    onOpenAlbum: (AlbumGroupKey) -> Unit,
    onAlbumMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val composer = uiState.composer
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val title = composer?.name ?: unknown
    val subtitle = composer?.let { pluralString(R.plurals.composer_works, it.works.size) + " · " + pluralString(R.plurals.composer_albums, it.albums.size) }
    val worksHeader = stringResource(R.string.composer_works_header)
    val albumsHeader = stringResource(R.string.composer_albums_header)
    var recordingsOf by rememberSaveable { mutableStateOf<String?>(null) }

    when {
        uiState.loading -> LibraryDetailScaffold(DetailContentState.Loading, title, null, null, ArtworkPlaceholder.Album, onNavigateUp, {}, {}, {}, modifier) {}

        composer == null -> LibraryDetailScaffold(DetailContentState.NotFound, title, null, null, ArtworkPlaceholder.Album, onNavigateUp, {}, {}, {}, modifier) {}

        else -> DetailScaffold(
            title = title,
            subtitle = subtitle,
            onNavigateUp = onNavigateUp,
            modifier = modifier.testTag("composer-detail"),
            hero = { topInset, _ -> ComposerHero(title, subtitle.orEmpty(), topInset) },
        ) {
            item(key = "works-header", contentType = "header") { SectionHeader(title = worksHeader) }
            items(composer.works, key = { "work-${it.key}" }, contentType = { "work" }) { work ->
                WorkRow(work, onClick = {
                    if (work.recordings.size == 1) onOpenAlbum(work.recordings.first().groupKey) else recordingsOf = work.key
                })
            }
            if (uiState.albums.isNotEmpty()) {
                albumShelf(
                    key = "composer-albums",
                    title = albumsHeader,
                    albums = uiState.albums,
                    unknown = unknown,
                    onClick = { album -> album.groupKey?.let(onOpenAlbum) },
                    onLongClick = onAlbumMore,
                )
            }
        }
    }

    composer?.works?.firstOrNull { it.key == recordingsOf }?.let { work ->
        OtherRecordingsSheet(
            title = work.title,
            recordings = work.recordings,
            onOpen = { key ->
                recordingsOf = null
                onOpenAlbum(key)
            },
            onDismissRequest = { recordingsOf = null },
        )
    }
}

@Composable
private fun ComposerHero(name: String, subtitle: String, topInset: Dp) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("detail-hero")
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
            .padding(start = S2Spacing.medium, end = S2Spacing.medium, top = topInset + S2Spacing.large, bottom = S2Spacing.medium),
    ) {
        S2Text(stringResource(R.string.composer_eyebrow).uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = colors.primary)
        S2Text(name, style = MaterialTheme.typography.heroTitle, color = colors.onSurface)
        S2Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun WorkRow(work: ComposerWork, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = S2TouchTarget.minimum)
            .padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            S2Text(work.title, style = MaterialTheme.typography.rowTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val recordings = pluralString(R.plurals.composer_recordings, work.recordings.size)
            S2Text(
                listOfNotNull(work.catalogue, recordings).joinToString(" · "),
                style = MaterialTheme.typography.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        S2Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ComposerDetailDestination(
    route: ComposerRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = assistedMetroViewModel<ComposerViewModel, ComposerViewModel.Factory> { create(route.composer) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        ComposerDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onOpenAlbum = { key -> onOpen(AlbumRoute(key.key, key.albumArtistGroupKey?.key, key.identity)) },
            onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
        )
    }
}
