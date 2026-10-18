package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.movementLabel
import com.simplecityapps.shuttle.model.showsMovementLabel
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.downloads.DownloadStatusHeader
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.common.utils.rememberMinutesFormat
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState
import com.simplecityapps.shuttle.ui.shell.albumArtworkKey

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
    // The artwork tints the whole screen, as the player does, when Colour from artwork is on.
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

/** The song's artist when it isn't [album]'s own (a guest, a compilation's track), else null. */
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
