package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** An artist in a list: circular [artwork] and a [summary] (album and song counts) as the secondary line. */
@Composable
fun ArtistRow(
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
private fun ArtistRowPreview() {
    val artist = SampleLibrary.artists.first()
    S2Preview {
        ArtistRow(
            name = artist.name,
            onClick = {},
            summary = "${artist.albums.size} albums · ${artist.songCount} songs",
            artwork = { Artwork(ArtworkPlaceholder.Artist, shape = ArtworkShape.Circle, model = artist) },
            onMore = {},
        )
    }
}
