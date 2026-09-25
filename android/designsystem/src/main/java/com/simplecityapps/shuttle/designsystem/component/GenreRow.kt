package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** A genre in a list: the genre placeholder (or artwork) and the [songCount] ("24 songs") as the secondary line. */
@Composable
fun GenreRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    songCount: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    MediaRow(
        title = name,
        onClick = onClick,
        modifier = modifier,
        supporting = songCount,
        leading = artwork,
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

@Preview
@Composable
private fun GenreRowPreview() {
    val genre = SampleLibrary.genres.first()
    S2Preview {
        GenreRow(
            name = genre.name,
            onClick = {},
            songCount = "${genre.songs.size} songs",
            artwork = { Artwork(ArtworkPlaceholder.Genre) },
            onMore = {},
        )
    }
}
