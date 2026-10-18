package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumRow
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun AlbumDetailDestination(
    route: AlbumRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val resources = LocalResources.current
    val viewModel = assistedMetroViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory> { create(route.groupKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var recordingsOf by remember { mutableStateOf<Long?>(null) }
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        val recordings = recordingsOf?.let { uiState.otherRecordings[it] }
        if (recordingsOf != null && recordings != null) {
            OtherRecordingsSheet(
                title = uiState.rows?.rows.orEmpty().filterIsInstance<AlbumRow.WorkHeader>().firstOrNull { it.songs.first().id == recordingsOf }?.title.orEmpty(),
                recordings = recordings,
                onOpen = { key ->
                    recordingsOf = null
                    onOpen(AlbumRoute(key.key, key.albumArtistGroupKey?.key, key.identity))
                },
                onDismissRequest = { recordingsOf = null },
            )
        }
        AlbumDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = {
                val selection = MediaSelection.Songs(uiState.songs)
                actions.dispatch(if (uiState.rows != null) MediaAction.ShuffleWorks(selection, uiState.playContext) else MediaAction.Shuffle(selection, uiState.playContext))
            },
            onPlayWork = { songs -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), 0, uiState.playContext)) },
            onToggleWork = viewModel::onToggleWork,
            onWorkMore = { title, songs ->
                val firstSongId = songs.first().id
                val count = uiState.otherRecordings[firstSongId]?.size ?: 0
                val extraActions = if (count > 0) {
                    listOf(S2Action(resources.getQuantityString(R.plurals.album_detail_other_recordings, count, count), { recordingsOf = firstSongId }, Icons.Rounded.LibraryMusic))
                } else {
                    emptyList()
                }
                actions.showActions(MediaActionsTarget(title, null, MediaSelection.Songs(songs), ArtworkPlaceholder.Album, extraActions = extraActions))
            },
            onOtherRecordings = { recordingsOf = it },
            onOpenComposer = { composer -> onOpen(ComposerRoute(composer)) },
            onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
            onOpenAlbum = { album -> onOpen(album.route) },
            onMoreByAlbumMore = { album -> actions.showActions(albumMoreTarget(album, resources.getString(R.string.menu_title_view_album)) { onOpen(album.route) }) },
        )
    }
}
