package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.simplecityapps.shuttle.designsystem.theme.artworkRole
import com.simplecityapps.shuttle.designsystem.theme.legibleGroundTop

/**
 * The sheet's colour: the player scheme's container, one tonal step up in dark mode, where the
 * container barely parts from the library behind the sheet's edge.
 */
internal val PlayerSheetColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

/**
 * Now Playing's ground over [PlayerSheetColor]. Under artwork it is the cover's primaryContainer (a dark tone in a dark
 * theme), fading into the sheet colour by [GroundFadeEnd] of the height. Its top colour is one that onSurface text, the
 * seek bar and the status bar's icons clear over every shade of the fade ([legibleGroundTop]), so no layout needs the
 * content to sit below it. Without artwork it draws nothing.
 */
@Composable
internal fun PlayerGround(modifier: Modifier = Modifier) {
    val base = PlayerSheetColor
    val scheme = MaterialTheme.colorScheme
    val top = artworkRole(base, remember(scheme, base) { scheme.legibleGroundTop(base) })
    if (top != base) {
        Spacer(
            modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(top, base), endY = size.height * GroundFadeEnd))
            },
        )
    }
}

private const val GroundFadeEnd = 0.55f

/** The skips, toggles and bar buttons' colour: onSurface, on the sheet colour below the ground's fade. */
internal val PlayerControlsColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

/** The bar's text buttons' colour: [PlayerControlsColor] under artwork, the buttons' own primary without. */
internal val PlayerTextButtonColor: Color
    @Composable get() = artworkRole(MaterialTheme.colorScheme.primary, PlayerControlsColor)

/** The panel sheet's colour, and its rows': a step apart from [PlayerSheetColor], lighter in light mode and in dark. */
internal val PanelColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

/** The wash a panel's header starts from: the cover's secondaryContainer over [PanelColor], none without artwork. */
internal val PanelWashTop: Color
    @Composable get() {
        val color = PanelColor
        return artworkRole(color, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = PanelWashAlpha).compositeOver(color))
    }

private const val PanelWashAlpha = 0.6f

internal object PlayerTestTags {
    const val Sheet = "player_sheet"
    const val MiniPlayer = "player_mini"
    const val NowPlaying = "player_now_playing"
    const val NowPlayingArtwork = "player_now_playing_artwork"
    const val Transport = "player_transport"
    const val QueueList = "player_queue_list"
    const val QueueHeader = "player_queue_header"
    const val QueueSource = "player_queue_source"
    const val QueueRow = "player_queue_row"
    const val QueueUpNext = "player_queue_up_next"
    const val Bar = "player_bar"
    const val PanelSheet = "player_panel_sheet"
    const val Scrim = "player_scrim"
    const val Pane = "player_pane"
    const val SleepTimerPanel = "player_sleep_timer_panel"
    const val PlaybackSoundPanel = "player_playback_sound_panel"
    const val LyricsPanel = "player_lyrics_panel"
}

/** Drops this node and its children from the semantics tree while [hidden]. */
internal fun Modifier.hiddenFromSemantics(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this
