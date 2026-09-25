package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import kotlinx.coroutines.launch

/** The persistent player pane's width: 360 dp, 412 dp at Extra-large (app-shell.md, section 2). */
fun playerPaneWidth(extraLarge: Boolean): Dp = if (extraLarge) 412.dp else 360.dp

/**
 * The trailing player pane from 1200 dp, shown at NowPlaying; Mini collapses it to the docked mini
 * player. It holds the compact sheet's list and bar without the sheet: no gesture drives it, and a
 * panel opens by scrolling the list alone.
 */
@Composable
internal fun PlayerPane(
    state: PlayerSheetState,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    width: Dp,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val panels = rememberNowPlayingPanels(listState, state, player, actions)
    val transportScrolledAway by rememberTransportScrolledAway(listState)
    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = modifier.width(width).fillMaxHeight().testTag(PlayerTestTags.Pane),
            color = PlayerSheetColor,
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val fixed = HandleHeight + with(LocalDensity.current) { titleHeight().toDp() } + transportHeight(NowPlayingGap) + NowPlayingGap * 2
                    val artwork = minOf(maxWidth - NowPlayingMargin * 2, MaxArtworkSize, maxHeight - fixed).coerceAtLeast(0.dp)
                    NowPlayingList(
                        player = player,
                        progress = progress,
                        actions = actions,
                        listState = listState,
                        artworkSize = artwork,
                        artworkSlotHeight = artwork + NowPlayingGap * 2,
                        viewportHeight = maxHeight,
                        onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                        onOpenRoute = onOpenRoute,
                        modifier = Modifier.testTag(PlayerTestTags.NowPlaying),
                    )
                    PinnedSong(player, actions, visible = transportScrolledAway, onClick = panels::scrollToTitle)
                }
                NowPlayingBar(
                    player = player,
                    actions = actions,
                    selected = player.panel,
                    onPanel = panels::toggle,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                )
            }
        }
    }
}
