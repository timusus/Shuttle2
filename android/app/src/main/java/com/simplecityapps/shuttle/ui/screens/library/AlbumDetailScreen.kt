package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.MovementRow
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SectionHeaderStyle
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.component.WorkHeaderRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.movementLabel
import com.simplecityapps.shuttle.model.showsMovementLabel
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.downloads.DownloadStatusHeader
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.common.utils.rememberMinutesFormat
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumRow
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.WorkTap
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import com.simplecityapps.shuttle.ui.shell.albumArtworkKey
import com.simplecityapps.shuttle.ui.text.stringResource
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** Album detail (inventory §1): the album's songs by disc, Play / Shuffle, the album's actions in the overflow, and More by the artist's other albums. */
@Composable
fun AlbumDetailScreen(
    uiState: AlbumDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onMoreByAlbumMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
    onPlayWork: (songs: List<Song>) -> Unit = {},
    onToggleWork: (firstSongId: Long) -> Unit = {},
    onWorkMore: (title: String, songs: List<Song>) -> Unit = { _, _ -> },
    onOtherRecordings: (firstSongId: Long) -> Unit = {},
    onOpenComposer: (composer: String) -> Unit = {},
) {
    val album = uiState.album
    val state = when {
        uiState.loadingState == AlbumDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        album == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val songs = uiState.songs
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    // Titled from the album artists the shelf lists, not the album's raw tag ("A feat. B")
    val moreByTitle = if (uiState.moreByArtistNames.isEmpty()) {
        stringResource(R.string.album_detail_more_by_artist)
    } else {
        stringResource(R.string.album_detail_more_by, uiState.moreByArtistNames.joinToString(", "))
    }
    // The artwork tints the whole screen, as the player does, when Colour from artwork is on (#496).
    ArtworkTheme(uiState.seed) {
        LibraryDetailScaffold(
            state = state,
            title = album?.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            subtitle = album?.let { if (uiState.rows != null) albumSubtitle(it, performers = null, withArtist = false) else albumSubtitle(it, uiState.performers) },
            eyebrow = uiState.rows?.eyebrow,
            onEyebrowClick = uiState.rows?.eyebrowComposer?.let { composer -> { onOpenComposer(composer) } },
            eyebrowLabel = uiState.rows?.eyebrow?.let { stringResource(R.string.composer_open, it) },
            credits = uiState.rows?.credits,
            artwork = album,
            placeholder = ArtworkPlaceholder.Album,
            artworkSize = ArtworkSize.Grid,
            artworkKey = album?.let { albumArtworkKey(it.route) },
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(songs, 0) },
            onShuffle = onShuffle,
            onMore = { album?.let(onAlbumMore) },
            header = { DownloadStatusHeader(songs, Modifier.padding(top = S2Spacing.smallMedium)) },
            modifier = modifier.testTag("album-detail"),
        ) {
            val rows = uiState.rows
            if (rows != null) {
                classicalRows(rows.rows, album, songs, onPlay, onPlayWork, onToggleWork, onWorkMore, onOtherRecordings, onSongMore, onOpenComposer)
            } else {
                val discs = songs.groupBy { it.disc ?: 1 }.toSortedMap()
                discs.forEach { (disc, discSongs) ->
                    if (discs.size > 1) {
                        item(key = "disc-$disc", contentType = "disc") {
                            SectionHeader(title = stringResource(R.string.album_detail_disc, disc))
                        }
                    }
                    items(discSongs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                        SongRow(
                            title = if (uiState.classical && song.showsMovementLabel) song.movementLabel() else song.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                            subtitle = song.artistUnlessAlbumArtist(album),
                            onClick = { onPlay(songs, songs.indexOf(song)) },
                            trackNumber = song.track,
                            duration = formatDuration(song.duration.toLong()),
                            playing = song.id == uiState.currentSong?.id,
                            offlineState = song.offlineState(),
                            onMore = { onSongMore(song) },
                            tabularDuration = true,
                        )
                    }
                }
            }
            if (uiState.moreByArtist.isNotEmpty()) {
                albumShelf(
                    key = "album-more-by",
                    title = moreByTitle,
                    albums = uiState.moreByArtist,
                    unknown = unknown,
                    onClick = onOpenAlbum,
                    onLongClick = onMoreByAlbumMore,
                )
            }
        }
    }
}

private fun LazyListScope.classicalRows(
    rows: List<AlbumRow>,
    album: Album?,
    songs: List<Song>,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onPlayWork: (songs: List<Song>) -> Unit,
    onToggleWork: (firstSongId: Long) -> Unit,
    onWorkMore: (title: String, songs: List<Song>) -> Unit,
    onOtherRecordings: (firstSongId: Long) -> Unit,
    onSongMore: (Song) -> Unit,
    onOpenComposer: (composer: String) -> Unit,
) {
    items(rows, key = { it.key }, contentType = { it::class }) { row ->
        when (row) {
            is AlbumRow.ComposerHeading -> {
                val composer = row.composer
                val label = stringResource(R.string.composer_open, row.name)
                SectionHeader(
                    title = row.name,
                    modifier = if (composer == null) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button) { onOpenComposer(composer) }.semantics { contentDescription = label }
                    },
                )
            }

            is AlbumRow.ActHeader -> SectionHeader(title = row.label, style = SectionHeaderStyle.Label)

            is AlbumRow.DiscDivider -> SectionHeader(title = stringResource(R.string.album_detail_disc, row.disc))

            is AlbumRow.WorkHeader -> WorkHeaderRow(
                title = row.title,
                detail = row.detail,
                meta = "${stringResource(row.count)} · ${formatDuration(row.durationMs)}",
                credits = row.credits,
                collapsed = if (row.collapsible) row.collapsed else null,
                playing = row.playing,
                onPlay = { onPlayWork(row.songs) },
                onClick = {
                    when (row.tap) {
                        WorkTap.ToggleCollapse -> onToggleWork(row.songs.first().id)
                        WorkTap.Play -> onPlayWork(row.songs)
                    }
                },
                onLongClick = { onWorkMore(row.title, row.songs) },
                otherRecordings = if (row.otherRecordings > 0) pluralString(R.plurals.album_detail_other_recordings, row.otherRecordings) else null,
                onOtherRecordings = { onOtherRecordings(row.songs.first().id) },
            )

            is AlbumRow.Movement -> MovementRow(
                numeral = row.numeral,
                name = row.name,
                duration = formatDuration(row.song.duration.toLong()),
                playing = row.playing,
                onClick = { onPlay(songs, row.playIndex) },
                onLongClick = { onSongMore(row.song) },
                onMore = { onSongMore(row.song) },
            )

            is AlbumRow.Track -> SongRow(
                title = row.title ?: stringResource(com.simplecityapps.core.R.string.unknown),
                subtitle = row.song.artistUnlessAlbumArtist(album),
                onClick = { onPlay(songs, row.playIndex) },
                trackNumber = row.song.track,
                duration = formatDuration(row.song.duration.toLong()),
                playing = row.playing,
                offlineState = row.song.offlineState(),
                onMore = { onSongMore(row.song) },
                tabularDuration = true,
            )
        }
    }
}

/** The song's artist when it isn't [album]'s own (a guest, a compilation's track), else null: the hero already names it (#739). */
internal fun Song.artistUnlessAlbumArtist(album: Album?): String? = friendlyArtistName?.takeIf { it.isNotBlank() && it != (album?.albumArtist ?: album?.friendlyArtistName) }

/** The actions target for an album's overflow menu, with a "Go to album" action that runs [onOpen]. */
internal fun albumMoreTarget(album: Album, goToAlbumLabel: String, onOpen: () -> Unit): MediaActionsTarget = MediaActionsTarget(
    title = album.name.orEmpty(),
    subtitle = album.friendlyArtistName,
    selection = MediaSelection.Albums(album),
    placeholder = ArtworkPlaceholder.Album,
    extraActions = listOf(S2Action(goToAlbumLabel, onOpen, Icons.Rounded.Album)),
)

/** "Artist · year · N songs · duration", without the artist on a classical page, whose credits line names them. */
@Composable
private fun albumSubtitle(
    album: Album,
    performers: String?,
    withArtist: Boolean = true,
): String {
    val formatMinutes = rememberMinutesFormat()
    // Whole minutes, rounded down; an album under a minute shows no runtime.
    val minutes = (album.duration.toLong() / 60_000).toInt()
    return listOfNotNull(
        album.friendlyArtistName.takeIf { withArtist },
        performers,
        album.year?.toString(),
        pluralString(R.plurals.songsPlural, album.songCount),
        if (minutes > 0) formatMinutes(minutes) else null,
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

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
