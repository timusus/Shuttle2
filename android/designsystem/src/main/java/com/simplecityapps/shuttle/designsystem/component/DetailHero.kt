package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.heroSubtitle
import com.simplecityapps.shuttle.designsystem.theme.heroTitle

/**
 * The head of an artwork detail screen (album, artist, playlist, genre): [artwork] centred on a wash of the scheme's
 * `primaryContainer` that fades into `surface` by the title, then [title], [subtitle], the Play / Shuffle [actions] and
 * any [extra]. Under `ArtworkTheme` the wash is the artwork's own colour.
 *
 * No parallax and no scrim (design-language.md): it scrolls with the list. [topInset] is the height of the pinned bar
 * it starts under, so the wash runs up behind the bar while the artwork sits clear of it.
 */
@Composable
fun DetailHero(
    title: String,
    subtitle: String?,
    artwork: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    extra: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().testTag("detail-hero")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
                .padding(top = topInset + S2Spacing.small, bottom = S2Spacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            artwork()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.xsmall),
        ) {
            Text(title, style = MaterialTheme.typography.heroTitle, color = colors.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.heroSubtitle, color = colors.onSurfaceVariant)
            }
            Box(Modifier.padding(top = S2Spacing.smallMedium)) { actions() }
            extra()
        }
    }
}

/**
 * The head of an artist page (#781): [image] full-bleed, square on a phone and 4:3 from [WideBleedWidth], running up
 * behind the pinned bar, with a scrim at the top for the bar's icons and one at the bottom behind the white [title] and
 * [subtitle]. The Play / Shuffle [actions] and any [extra] follow below it. The scheme's `surfaceContainerHighest`
 * shows while the image loads, or when there's none: no placeholder glyph.
 */
@Composable
fun DetailBleedHero(
    title: String,
    subtitle: String?,
    image: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().testTag("detail-hero")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (maxWidth >= WideBleedWidth) 4f / 3f else 1f)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag("detail-hero-image"),
            ) {
                Box(Modifier.fillMaxSize()) { image() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.3f)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(S2Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.xsmall),
                ) {
                    Text(title, style = MaterialTheme.typography.heroTitle, color = Color.White)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.heroSubtitle, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = S2Spacing.smallMedium),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.xsmall),
        ) {
            actions()
            extra()
        }
    }
}

/** From this width, a tablet's or an unfolded phone's, [DetailBleedHero] is 4:3 rather than square, so it leaves room for the list. */
private val WideBleedWidth = 600.dp

@Preview
@Composable
private fun DetailHeroPreview() {
    S2Preview {
        DetailHero(
            title = "Night Bus Frequencies",
            subtitle = "Oda Kestrel Quartet · 2024 · 9 songs · 41:12",
            artwork = { Artwork(model = null, placeholder = ArtworkPlaceholder.Album, size = ArtworkSize.Hero) },
            actions = {
                S2ButtonGroup(
                    primary = S2GroupAction("Play", {}, Icons.Rounded.PlayArrow),
                    secondary = listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)),
                )
            },
        )
    }
}

@Preview
@Composable
private fun DetailBleedHeroPreview() {
    S2Preview {
        DetailBleedHero(
            title = "Oda Kestrel Quartet",
            subtitle = "3 albums · 27 songs",
            image = {},
            actions = {
                S2ButtonGroup(
                    primary = S2GroupAction("Play", {}, Icons.Rounded.PlayArrow),
                    secondary = listOf(S2GroupAction("Shuffle", {}, Icons.Rounded.Shuffle)),
                )
            },
        )
    }
}
