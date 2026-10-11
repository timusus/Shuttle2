package com.simplecityapps.shuttle.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

private typealias ShellScope = AnimatedContentTransitionScope<Scene<NavKey>>

/**
 * How the shell's [androidx.navigation3.ui.NavDisplay] moves between screens: a shared-axis slide for a push or pop, a
 * fade-through when the move is between tabs, and the Material predictive-back scale while a back gesture is dragged.
 */
internal class ShellTransitions(
    val transition: ShellScope.() -> ContentTransform,
    val popTransition: ShellScope.() -> ContentTransform,
    val predictivePopTransition: ShellScope.(Int) -> ContentTransform,
)

@Composable
internal fun rememberShellTransitions(navigator: AppNavigator): ShellTransitions {
    val motion = MaterialTheme.motionScheme
    val distance = with(LocalDensity.current) { S2Spacing.xlarge.roundToPx() }
    return remember(navigator, motion, distance) {
        fun ShellScope.betweenTabs() = tabOf(navigator, initialState) != tabOf(navigator, targetState)

        val fadeThrough = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = FadeThroughScale) togetherWith
            fadeOut(motion.fastEffectsSpec())

        ShellTransitions(
            transition = {
                if (betweenTabs()) {
                    fadeThrough
                } else {
                    (slideInHorizontally(motion.defaultSpatialSpec()) { distance } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(motion.defaultSpatialSpec()) { -distance } + fadeOut(motion.fastEffectsSpec()))
                }
            },
            popTransition = {
                if (betweenTabs()) {
                    fadeThrough
                } else {
                    (slideInHorizontally(motion.defaultSpatialSpec()) { -distance } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(motion.defaultSpatialSpec()) { distance } + fadeOut(motion.fastEffectsSpec()))
                }
            },
            predictivePopTransition = {
                (slideInHorizontally(motion.defaultSpatialSpec()) { -distance } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                    (scaleOut(motion.defaultSpatialSpec(), targetScale = PredictivePopScale) + fadeOut(motion.fastEffectsSpec()))
            },
        )
    }
}

// The tab whose stack holds the scene's top screen; null once that screen has been popped.
private fun tabOf(navigator: AppNavigator, scene: Scene<NavKey>): ShellTab? {
    val key = scene.entries.lastOrNull()?.contentKey ?: return null
    return ShellTab.entries.firstOrNull { tab -> key in navigator.stack(tab) }
}

private const val FadeThroughScale = 0.92f
private const val PredictivePopScale = 0.9f
