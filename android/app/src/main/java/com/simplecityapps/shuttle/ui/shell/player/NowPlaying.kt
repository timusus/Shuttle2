package com.simplecityapps.shuttle.ui.shell.player

import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.navigation3.runtime.NavKey
import com.google.android.gms.cast.framework.CastButtonFactory
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControls
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControlsSize
import com.simplecityapps.shuttle.designsystem.component.S2SeekBar

/** The Cast framework's route button, themed to the player's scheme. Only shown where Cast can start. */
@Composable
internal fun CastButton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_cast)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        factory = { context ->
            // The route button reads AppCompat colours from its context; don't depend on the host activity's theme for them.
            MediaRouteButton(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)).also { button ->
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button)
                button.contentDescription = description
            }
        },
        update = { button ->
            ContextCompat.getDrawable(button.context, androidx.mediarouter.R.drawable.mr_button_light)?.mutate()?.let { drawable ->
                drawable.setTint(tint)
                button.setRemoteIndicatorDrawable(drawable)
            }
        },
        modifier = modifier.size(48.dp),
    )
}

/** The artwork, square and as large as its slot allows, up to [MaxArtworkSize], with [gap] above and below it. Swiping it sideways skips. */
@Composable
internal fun NowPlayingArtwork(
    player: PlayerUiState,
    actions: PlayerActions,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    var boxModifier = modifier.fillMaxWidth().padding(horizontal = NowPlayingMargin, vertical = gap).testTag(PlayerTestTags.NowPlayingArtwork)
    if (player.current != null) boxModifier = boxModifier.skipSwipe(onNext = actions::skipToNext, onPrevious = actions::skipToPrevious)
    Box(boxModifier, contentAlignment = Alignment.Center) {
        player.current?.let { current ->
            SongArtwork(current.song, Modifier.widthIn(max = MaxArtworkSize).aspectRatio(1f, matchHeightConstraintsFirst = true), size = ArtworkSize.Hero)
        }
    }
}

/** The title and artist beside the favourite toggle, sitting a small step above the seek bar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NowPlayingTitle(
    player: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val current = player.current
    Row(modifier.fillMaxWidth().padding(start = NowPlayingMargin + 4.dp, end = 8.dp, bottom = TitleSeekGap), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = current?.title.orEmpty(), style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(current?.artist, current?.album).joinToString(" • "),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        S2IconToggleButton(
            icon = Icons.Rounded.FavoriteBorder,
            checkedIcon = Icons.Rounded.Favorite,
            contentDescription = stringResource(R.string.menu_title_favorite),
            checked = player.favourite,
            onCheckedChange = { actions.toggleFavourite() },
            enabled = current != null,
        )
    }
}

/**
 * The artwork over the title, [gap] apart. With [fillHeight] the artwork's slot takes all the height
 * the title leaves, centring the artwork in it, so the title stays on whatever sits below; without it
 * the two wrap and centre together as a group.
 */
@Composable
internal fun NowPlayingSong(
    player: PlayerUiState,
    actions: PlayerActions,
    gap: Dp,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        NowPlayingArtwork(player, actions, gap, Modifier.weight(1f, fill = fillHeight))
        NowPlayingTitle(player, actions)
    }
}

/**
 * The seek bar over the transport controls, [gap] between and below them: which heads the open
 * panel once the artwork has scrolled away ([transportHeight]). The Large controls sit closer to the edges than
 * the seek bar, and scale down where even that doesn't fit.
 */
@Composable
internal fun Transport(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(transportHeight(gap)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeekBar(player, progress, actions)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            S2PlayerControls(
                playing = player.playing,
                onPlayPause = actions::togglePlayback,
                onPrevious = actions::skipToPrevious,
                onNext = actions::skipToNext,
                shuffle = player.shuffle,
                onShuffleChange = { actions.toggleShuffle() },
                repeatMode = player.repeatMode,
                onRepeatClick = actions::cycleRepeatMode,
                buffering = player.buffering,
                size = S2PlayerControlsSize.Large,
            )
        }
    }
}

/** Reads the ticking position in its own scope, so only the seek bar recomposes with it. */
@Composable
private fun SeekBar(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
) {
    val current = progress()
    S2SeekBar(
        positionMs = current.positionMs,
        durationMs = current.durationMs,
        onSeek = actions::seekTo,
        playing = player.playing,
        enabled = player.current != null,
        modifier = Modifier.fillMaxWidth().height(SeekBarHeight).padding(horizontal = NowPlayingMargin),
    )
}

/** The largest the Now Playing artwork grows, however much room there is. */
internal val MaxArtworkSize = 480.dp
