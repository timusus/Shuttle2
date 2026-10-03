package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.tileSubtitle
import com.simplecityapps.shuttle.designsystem.theme.tileTitle
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** How far a selected tile's artwork shrinks, leaving a margin that marks it as picked (as Google Photos does). */
private const val SelectedArtworkScale = 0.88f

/**
 * An album, artist or playlist in a grid or a Home shelf: the bare [artwork] in a square slot with
 * the title and [subtitle] under it, no card behind them. The caller sizes the tile; the artwork
 * fills the slot (`Artwork(modifier = Modifier.fillMaxSize())`) and clips itself to its own shape.
 *
 * [selected] shrinks the artwork inside a `secondaryContainer` tile and badges it with a check;
 * [playing] marks the album or artist the current song belongs to, like [SongRow] does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    title: String,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val artworkScale by animateFloatAsState(
        if (selected) SelectedArtworkScale else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "gridTileSelection",
    )
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large) else Modifier),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    },
            ) { artwork() }
            if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = S2Spacing.xsmall, top = S2Spacing.small, end = S2Spacing.xsmall, bottom = S2Spacing.xsmall),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        stringResource(R.string.ds_now_playing),
                        Modifier.size(S2IconSize.small),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(S2Spacing.xsmall))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.tileTitle,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.tileSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(S2IconSize.small))
    }
}

@Preview
@Composable
private fun GridTilePreview() {
    val album = SampleLibrary.albums.first()
    S2Preview {
        GridTile(
            title = album.title,
            subtitle = album.artist,
            onClick = {},
            artwork = { Artwork(ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid, model = album) },
            modifier = Modifier.width(176.dp),
        )
    }
}
