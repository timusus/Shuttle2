package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.theme.S2Accent
import com.simplecityapps.shuttle.designsystem.theme.accentColorScheme
import com.simplecityapps.shuttle.ui.shell.ShellRequest

/**
 * The now playing widget, shared by the small and large widget receivers. It only draws
 * [NowPlayingWidgetState]; [WidgetManager] writes that state and asks for a redraw.
 */
class NowPlayingWidget : GlanceAppWidget() {
    // Exact rather than responsive: the artwork is sized to fill the space up to the padding, which only
    // works when the layout knows the widget's real size rather than the nearest breakpoint below it.
    override val sizeMode = SizeMode.Exact

    override val stateDefinition = NowPlayingWidgetStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else fallbackColors) {
                NowPlayingContent(
                    state = currentState(),
                    layout = widgetLayoutFor(LocalSize.current)
                )
            }
        }
    }
}

/** The widget palette where dynamic colour isn't available: the app's default, neutral scheme. */
private val fallbackColors =
    ColorProviders(
        light = accentColorScheme(S2Accent.Neutral, isDark = false),
        dark = accentColorScheme(S2Accent.Neutral, isDark = true)
    )

/** Visible to tests, which render it directly with a fixed [state] and [layout] instead of the live widget state. */
@Composable
internal fun NowPlayingContent(
    state: NowPlayingWidgetState,
    layout: WidgetLayout
) {
    // The hero's art fills the widget to its edges, so it insets its own text and buttons instead.
    val fullBleed = state.hasTrack && layout.mode == WidgetMode.Hero
    WidgetContainer(
        padding = if (fullBleed) 0.dp else layout.padding,
        bottomPadding = if (fullBleed) 0.dp else layout.bottomPadding,
        opacity = state.backgroundOpacity
    ) {
        if (!state.hasTrack) {
            IdleContent(layout)
        } else {
            when (layout.mode) {
                WidgetMode.Row -> RowContent(state, layout)
                WidgetMode.Card -> CardContent(state, layout)
                WidgetMode.Split -> SplitContent(state, layout)
                WidgetMode.Hero -> HeroContent(state, layout)
            }
        }
    }
}

/** Tapping the widget opens the full player in the app's launcher activity, found through the package manager so the widget needn't name it. */
@Composable
private fun openAppModifier(): GlanceModifier {
    val context = LocalContext.current
    val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return GlanceModifier
    // Glance passes each action parameter as an intent extra under the key's name.
    val openPlayer = actionParametersOf(ActionParameters.Key<Boolean>(ShellRequest.EXTRA_OPEN_PLAYER) to true)
    return GlanceModifier.clickable(actionStartActivity(component, openPlayer))
}

/**
 * The widget's background, rounded to the launcher's radius, with [padding] on every side of [content] but the
 * bottom, which has [bottomPadding]. The content starts at the top left, so nothing is centred away from the
 * edge it's anchored to.
 */
@Composable
private fun WidgetContainer(
    padding: Dp,
    bottomPadding: Dp,
    opacity: Int,
    content: @Composable () -> Unit
) {
    val background =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceModifier
                .background(backgroundColor(opacity))
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            GlanceModifier
        }
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .then(background)
                .then(openAppModifier())
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Views can't be clipped below API 31, so the rounded background is a drawable. A background
            // modifier can't be translucent, but an image can.
            Image(
                provider = ImageProvider(R.drawable.widget_background),
                contentDescription = null,
                alpha = widgetBackgroundAlpha(opacity),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground)
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(start = padding, top = padding, end = padding, bottom = bottomPadding),
            contentAlignment = Alignment.TopStart
        ) {
            content()
        }
    }
}

/**
 * The theme's widget background at the opacity setting. Glance can't add alpha to a theme colour, so below
 * full opacity the theme colour is resolved here for light and dark, and the launcher picks between them as
 * the system switches, just as it does for the theme colour itself.
 */
@Composable
private fun backgroundColor(opacity: Int): ColorProvider {
    val base = GlanceTheme.colors.widgetBackground
    val alpha = widgetBackgroundAlpha(opacity)
    if (alpha >= 1f) return base
    val context = LocalContext.current
    return ColorProvider(
        day = base.getColor(context.withNightMode(false)).copy(alpha = alpha),
        night = base.getColor(context.withNightMode(true)).copy(alpha = alpha)
    )
}

private fun Context.withNightMode(night: Boolean): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(configuration)
}
