package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** An album in a list: rounded [artwork], the artist as the secondary line, and [meta] (year, song count). */
@Composable
fun AlbumRow(
    title: String,
    artist: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    meta: String? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    MediaRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        supporting = artist,
        meta = meta,
        leading = artwork,
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

@Preview
@Composable
private fun AlbumRowPreview() {
    val album = SampleLibrary.albums.first()
    S2Preview {
        AlbumRow(
            title = album.title,
            artist = album.artist,
            onClick = {},
            artwork = { Artwork(ArtworkPlaceholder.Album, model = album) },
            meta = album.year.toString(),
            onMore = {},
        )
    }
}
