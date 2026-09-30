package com.simplecityapps.shuttle.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.ktx.animateColorScheme

/** The root theme choices nested themes (see [ArtworkTheme]) need to generate matching schemes. */
@Immutable
data class S2ThemeSettings(
    val isDark: Boolean,
    val accent: S2Accent,
    val contrast: S2Contrast,
)

val LocalS2ThemeSettings = staticCompositionLocalOf {
    S2ThemeSettings(isDark = false, accent = S2Accent.Default, contrast = S2Contrast.Default)
}

/** The root scheme [S2Theme] is heading to, before animation: the brand fallback for nested themes. */
internal val LocalRootColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** The default M3 type scale, which carries the emphasized styles (`displayLargeEmphasized` etc.). */
val S2Typography = Typography()

/**
 * S2's shape scale: every token a [ContinuousRoundedCornerShape], on radii a step softer than
 * stock M3's at the small end, with the Expressive tokens (`largeIncreased`, `extraLargeIncreased`,
 * `extraExtraLarge`). Surfaces that should read as S2 take their shape from here, not a literal.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val S2Shapes = Shapes(
    extraSmall = ContinuousRoundedCornerShape(6.dp),
    small = ContinuousRoundedCornerShape(10.dp),
    medium = ContinuousRoundedCornerShape(14.dp),
    large = ContinuousRoundedCornerShape(20.dp),
    extraLarge = ContinuousRoundedCornerShape(28.dp),
    largeIncreased = ContinuousRoundedCornerShape(24.dp),
    extraLargeIncreased = ContinuousRoundedCornerShape(32.dp),
    extraExtraLarge = ContinuousRoundedCornerShape(48.dp),
)

/**
 * The app's root theme: the user's accent (or Material You dynamic colour on Android 12+) as a
 * 2025-spec scheme, expressive motion, and the M3 type and shape scales. Accent, dark and contrast
 * changes crossfade per role.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: S2Accent = S2Accent.Default,
    contrast: S2Contrast = rememberSystemContrast(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val context = LocalContext.current
    val targetScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(accent, darkTheme, contrast) { accentColorScheme(accent, darkTheme, contrast) }
    }
    val colorScheme = animateSchemeChange(targetScheme, motionScheme)
    CompositionLocalProvider(
        LocalS2ThemeSettings provides S2ThemeSettings(darkTheme, accent, contrast),
        LocalRootColorScheme provides targetScheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = S2Shapes,
            typography = S2Typography,
            content = content,
        )
    }
}

/** Crossfades every role of [target] with the slow effects spring of [motionScheme]. */
@Composable
internal fun animateSchemeChange(target: ColorScheme, motionScheme: MotionScheme = MaterialTheme.motionScheme): ColorScheme = animateColorScheme(colorScheme = target, animationSpec = { motionScheme.slowEffectsSpec<Color>() })
