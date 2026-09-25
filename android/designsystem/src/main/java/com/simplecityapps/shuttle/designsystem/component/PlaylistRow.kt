package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/**
 * A playlist in a list, user or smart: the caller picks the [artwork] (a smart playlist uses the
 * [ArtworkPlaceholder.SmartPlaylist] placeholder) and the [summary] (song count, or "No songs").
 */
@Composable
fun PlaylistRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    MediaRow(
        title = name,
        onClick = onClick,
        modifier = modifier,
        supporting = summary,
        leading = artwork,
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

@Preview
@Composable
private fun PlaylistRowPreview() {
    val playlist = SampleLibrary.playlists.first()
    S2Preview {
        PlaylistRow(
            name = playlist.name,
            onClick = {},
            summary = "${playlist.songs.size} songs",
            artwork = { Artwork(ArtworkPlaceholder.Playlist) },
            onMore = {},
        )
    }
}
