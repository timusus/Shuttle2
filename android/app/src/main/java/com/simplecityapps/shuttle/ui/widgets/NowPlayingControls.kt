package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.playback.PlaybackServiceAction
import com.simplecityapps.playback.PlaybackServiceStarter
import com.simplecityapps.playback.R as PlaybackR
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.di.appGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/** Colours for text and controls, which change when they're drawn over artwork. */
internal data class ContentColors(
    val title: ColorProvider,
    val subtitle: ColorProvider,
    val icon: ColorProvider,
    val toggleOff: ColorProvider,
    val toggleOn: ColorProvider,
    val playBackground: ColorProvider,
    val playContent: ColorProvider
) {
    companion object {
        @Composable
        fun default() = GlanceTheme.colors.let {
            ContentColors(
                title = it.onSurface,
                subtitle = it.onSurfaceVariant,
                icon = it.onSurfaceVariant,
                toggleOff = it.onSurfaceVariant,
                toggleOn = it.primary,
                playBackground = it.primaryContainer,
                playContent = it.onPrimaryContainer
            )
        }

        /** Over the scrim: white, with toggles that are off dimmed, so the dot isn't the only cue. */
        @Composable
        fun onArt() = GlanceTheme.colors.let {
            ContentColors(
                title = ColorProvider(Color.White),
                subtitle = ColorProvider(Color.White.copy(alpha = 0.85f)),
                icon = ColorProvider(Color.White),
                toggleOff = ColorProvider(Color.White.copy(alpha = 0.7f)),
                toggleOn = ColorProvider(Color.White),
                playBackground = it.primaryContainer,
                playContent = it.onPrimaryContainer
            )
        }

        @Composable
        fun onPlaceholder() = GlanceTheme.colors.let {
            ContentColors(
                title = it.onSecondaryContainer,
                subtitle = it.onSecondaryContainer,
                icon = it.onSecondaryContainer,
                toggleOff = it.onSecondaryContainer,
                toggleOn = it.primary,
                // primaryContainer is too close to the placeholder's secondaryContainer to read as a button.
                playBackground = it.primary,
                playContent = it.onPrimary
            )
        }
    }
}

/**
 * Buttons spread evenly across the available width, so play/pause sits in the middle. When [compact], the play
 * button's circle is [WidgetDimens.compactPlay] inside its full-size target.
 */
@Composable
internal fun ButtonRow(
    buttons: List<WidgetButton>,
    state: NowPlayingWidgetState,
    colors: ContentColors = ContentColors.default(),
    compact: Boolean = false
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                if (compact && button == WidgetButton.PlayPause) {
                    CompactPlayButton(state, colors)
                } else {
                    ControlButton(button, state, colors)
                }
            }
        }
    }
}

/** Play/pause with a smaller circle than [CircleIconButton] draws, in the same 48dp touch target. */
@Composable
private fun CompactPlayButton(
    state: NowPlayingWidgetState,
    colors: ContentColors
) {
    val context = LocalContext.current
    val description = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play)
    // Rounding the target keeps its ripple a circle rather than a square around the smaller one.
    val round = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceModifier.cornerRadius(WidgetDimens.buttonSize / 2) else GlanceModifier
    Box(
        modifier =
            GlanceModifier
                .size(WidgetDimens.buttonSize)
                .then(round)
                .clickable(playbackAction(PlaybackServiceAction.TogglePlayback))
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_toggle_dot),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.playBackground),
            modifier = GlanceModifier.size(WidgetDimens.compactPlay)
        )
        Image(
            provider = ImageProvider(if (state.isPlaying) PlaybackR.drawable.ic_pause_black_24dp else PlaybackR.drawable.ic_play_arrow_black_24dp),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.playContent),
            modifier = GlanceModifier.size(S2IconSize.medium)
        )
    }
}

@Composable
internal fun ControlButton(
    button: WidgetButton,
    state: NowPlayingWidgetState,
    colors: ContentColors = ContentColors.default()
) {
    val context = LocalContext.current
    when (button) {
        WidgetButton.PlayPause ->
            CircleIconButton(
                imageProvider = ImageProvider(if (state.isPlaying) PlaybackR.drawable.ic_pause_black_24dp else PlaybackR.drawable.ic_play_arrow_black_24dp),
                contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.widget_play),
                onClick = playbackAction(PlaybackServiceAction.TogglePlayback),
                backgroundColor = colors.playBackground,
                contentColor = colors.playContent
            )

        WidgetButton.Previous ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_previous_black_24dp),
                contentDescription = context.getString(R.string.button_skip_previous),
                onClick = playbackAction(PlaybackServiceAction.SkipPrevious),
                backgroundColor = null,
                contentColor = colors.icon
            )

        WidgetButton.Next ->
            CircleIconButton(
                imageProvider = ImageProvider(PlaybackR.drawable.ic_skip_next_black_24dp),
                contentDescription = context.getString(R.string.button_skip_next),
                onClick = playbackAction(PlaybackServiceAction.SkipNext),
                backgroundColor = null,
                contentColor = colors.icon
            )

        WidgetButton.Shuffle ->
            ToggleButton(
                icon = PlaybackR.drawable.ic_shuffle_black_24dp,
                contentDescription = context.getString(if (state.shuffleOn) CoreR.string.shuffle_on else CoreR.string.shuffle_off),
                onClick = playbackAction(PlaybackServiceAction.ToggleShuffle),
                on = state.shuffleOn,
                colors = colors
            )

        WidgetButton.Repeat ->
            ToggleButton(
                icon = if (state.repeatMode == WidgetRepeatMode.One) R.drawable.ic_repeat_one_black_24dp else R.drawable.ic_repeat_black_24dp,
                contentDescription =
                    context.getString(
                        when (state.repeatMode) {
                            WidgetRepeatMode.Off -> R.string.widget_repeat_off
                            WidgetRepeatMode.All -> R.string.widget_repeat_all
                            WidgetRepeatMode.One -> R.string.widget_repeat_one
                        }
                    ),
                onClick = playbackAction(PlaybackServiceAction.ToggleRepeat),
                on = state.repeatMode != WidgetRepeatMode.Off,
                colors = colors
            )
    }
}

/**
 * Shuffle or repeat. When on, the icon takes the primary colour and a dot sits beneath it: under some dynamic
 * palettes primary is close to the off colour, so the tint alone doesn't read at a glance.
 */
@Composable
private fun ToggleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: Action,
    on: Boolean,
    colors: ContentColors
) {
    Box(modifier = GlanceModifier.size(WidgetDimens.buttonSize), contentAlignment = Alignment.BottomCenter) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = contentDescription,
            onClick = onClick,
            backgroundColor = null,
            contentColor = if (on) colors.toggleOn else colors.toggleOff
        )
        if (on) {
            Box(modifier = GlanceModifier.padding(bottom = 5.dp)) {
                Image(
                    provider = ImageProvider(R.drawable.widget_toggle_dot),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.toggleOn),
                    modifier = GlanceModifier.size(WidgetDimens.toggleDot)
                )
            }
        }
    }
}

internal val playbackActionKey = ActionParameters.Key<String>("playbackServiceAction")

private fun playbackAction(action: PlaybackServiceAction): Action = actionRunCallback<PlaybackActionCallback>(actionParametersOf(playbackActionKey to action.name))

/** Runs on the widget tap, where a foreground start is allowed, and hands the command to the playback service. */
class PlaybackActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[playbackActionKey]?.let(PlaybackServiceAction::valueOf) ?: return
        context.appGraph<Injector>().playbackServiceStarter().start(action)
    }

    @ContributesTo(AppScope::class)
    interface Injector {
        fun playbackServiceStarter(): PlaybackServiceStarter
    }
}
