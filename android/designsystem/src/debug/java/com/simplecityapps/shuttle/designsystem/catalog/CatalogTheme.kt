package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.S2Contrast
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * The scheme columns a board can be reviewed in: the brand accent and three fixed artwork seeds
 * (warm, cool, and one grey enough to test the fallback threshold), all selectable in the
 * on-device catalogue. [recorded] narrows the automated matrix ([catalogShots]) to Brand and Warm
 * — the default plus the most contrasting seed (near-complementary hue to the brand's azure, both
 * fully saturated; Cool sits close to the brand hue itself and LowChroma is desaturated, so
 * neither adds much visual contrast) — #553. [Dynamic] is Material You, on-device only: Robolectric
 * has no wallpaper colours, so it isn't recorded either.
 */
enum class CatalogScheme(val seed: Color?, val recorded: Boolean = true) {
    Brand(null),
    Warm(Color(0xFFD9542B)),
    Cool(Color(0xFF2F6FDE), recorded = false),
    LowChroma(Color(0xFFB5A898), recorded = false),
    Dynamic(null, recorded = false),
}

/** The scheme column a board is rendering in, for boards that show scheme-specific detail. */
val LocalCatalogScheme = staticCompositionLocalOf { CatalogScheme.Brand }

/**
 * Wraps a board in [S2Theme], then an [ArtworkTheme] seeded from [scheme], on a `surface`
 * background. [motionSlowdown] above 1 slows every spring for reviewing motion on device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogTheme(
    scheme: CatalogScheme,
    darkTheme: Boolean,
    contrast: S2Contrast = S2Contrast.Default,
    modifier: Modifier = Modifier.fillMaxSize(),
    motionSlowdown: Float = 1f,
    content: @Composable () -> Unit,
) {
    S2Theme(darkTheme = darkTheme, contrast = contrast, dynamicColor = scheme == CatalogScheme.Dynamic) {
        val baseMotion = MaterialTheme.motionScheme
        val motion = remember(baseMotion, motionSlowdown) {
            if (motionSlowdown == 1f) baseMotion else SlowMotionScheme(baseMotion, motionSlowdown)
        }
        MaterialExpressiveTheme(motionScheme = motion) {
            CompositionLocalProvider(LocalCatalogScheme provides scheme) {
                ArtworkTheme(seed = scheme.seed?.let(ArtworkSeed::Available) ?: ArtworkSeed.None) {
                    Surface(modifier = modifier, content = content)
                }
            }
        }
    }
}

/** Slows every spring of [base] by [factor]: a spring's period scales with 1/√stiffness. */
private class SlowMotionScheme(private val base: MotionScheme, private val factor: Float) : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.defaultSpatialSpec<T>().slowed()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec<T>().slowed()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = base.slowSpatialSpec<T>().slowed()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec<T>().slowed()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.fastEffectsSpec<T>().slowed()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = base.slowEffectsSpec<T>().slowed()

    private fun <T> FiniteAnimationSpec<T>.slowed(): FiniteAnimationSpec<T> = if (this is SpringSpec<T>) {
        spring(dampingRatio, stiffness / (factor * factor), visibilityThreshold)
    } else {
        this
    }
}
