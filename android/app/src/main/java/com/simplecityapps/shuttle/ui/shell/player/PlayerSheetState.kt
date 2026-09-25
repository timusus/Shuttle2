package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The shell's one player state (docs/architecture/app-shell.md, section 1): an
 * [AnchoredDraggableState] over [PlayerLevel] whose offset is the sheet's top edge.
 *
 * It is created above the width-class branch, so one instance, and its level, survives rotation,
 * resizing and folding; the level and the last [PlayerMode] are saved, so it also survives process
 * death. The pane (from 1200 dp) uses the same state over nominal anchors and snaps between them.
 * The open panel is the view model's ([PlayerUiState.panel]), beside the level.
 */
@Stable
class PlayerSheetState internal constructor(
    internal val draggable: AnchoredDraggableState<PlayerLevel>,
    modeState: MutableState<PlayerMode>,
    animationSpec: AnimationSpec<Float>,
) {
    var mode: PlayerMode by modeState
        private set

    /** Null until the queue has reported, so a restored level stands on a cold start. */
    var hasQueue: Boolean? by mutableStateOf(null)
        private set

    /** Anchors and fractions, in px. Nominal until the sheet is measured, and always in pane mode. */
    var geometry: PlayerSheetGeometry by mutableStateOf(NominalGeometry)
        private set

    /** Whether the sheet has been measured, so [geometry] is its own rather than the stand-in. */
    internal val measured: Boolean get() = geometry != NominalGeometry

    /** The spec for level changes; the shell sets it from the motion scheme. */
    internal var animationSpec: AnimationSpec<Float> = animationSpec

    /** A level change that anchors alone could not apply; the shell snaps to it. */
    internal var requestedLevel: PlayerLevel? by mutableStateOf(null)
        private set

    /** Set when a queue appears under a hidden sheet: the shell animates Hidden to Mini. */
    internal var revealPending: Boolean by mutableStateOf(false)
        private set

    /** Set when the queue empties under a raised sheet: the shell animates it down to Hidden. */
    internal var collapsePending: Boolean by mutableStateOf(false)
        private set

    /** The level the player is at, or heading for during a drag or animation. */
    val level: PlayerLevel get() = draggable.targetValue

    /** The level the player last came to rest at. */
    val settledLevel: PlayerLevel get() = draggable.settledValue

    val allowedLevels: Set<PlayerLevel>
        get() = playerLevels(mode, hasQueue ?: (draggable.currentValue != PlayerLevel.Hidden), geometry.partialRest)

    /** The sheet's current offset, or its settled level's anchor before the first layout. */
    val offset: Float
        get() = draggable.offset.takeUnless { it.isNaN() } ?: geometry.offsetOf(draggable.currentValue)

    /** Applies the latest window class and queue presence. Called after every shell composition. */
    internal fun configure(
        mode: PlayerMode,
        hasQueue: Boolean?,
    ) {
        val preferred = mapPlayerLevel(requestedLevel ?: draggable.targetValue, this.mode, mode)
        this.mode = mode
        this.hasQueue = hasQueue
        if (mode == PlayerMode.Pane) geometry = NominalGeometry
        sync(preferred)
    }

    /** Applies a newly measured sheet geometry, keeping the level the sheet was heading for. */
    internal fun onMeasured(geometry: PlayerSheetGeometry) {
        if (mode == PlayerMode.Pane || geometry == this.geometry) return
        this.geometry = geometry
        sync(requestedLevel ?: draggable.targetValue)
    }

    private fun sync(preferred: PlayerLevel) {
        val allowed = allowedLevels
        val target = resolvePlayerLevel(preferred, allowed)
        val reveal = preferred == PlayerLevel.Hidden && target == PlayerLevel.Mini
        val collapse = mode != PlayerMode.Pane && hasQueue == false && preferred != PlayerLevel.Hidden
        // A revealing sheet keeps Hidden as an anchor until it has animated up to Mini, and a collapsing one keeps
        // its levels until it has animated down to Hidden.
        val anchorLevels = when {
            collapse || collapsePending -> playerLevels(mode, hasQueue = true, geometry.partialRest) + PlayerLevel.Hidden
            reveal || revealPending -> allowed + PlayerLevel.Hidden
            else -> allowed
        }
        val anchors = DraggableAnchors { anchorLevels.forEach { it at geometry.offsetOf(it) } }
        val settleAt = when {
            reveal -> PlayerLevel.Hidden
            collapse -> preferred
            else -> target
        }
        if (reveal) revealPending = true
        if (collapse) collapsePending = true
        if (anchors != draggable.anchors) {
            draggable.updateAnchors(anchors, settleAt)
            requestedLevel = null
        } else if (!revealPending && !collapsePending && settleAt != draggable.targetValue) {
            requestedLevel = settleAt
        }
    }

    /** Snaps to [requestedLevel], if any. */
    internal suspend fun applyRequestedLevel() {
        val level = requestedLevel ?: return
        draggable.snapTo(level)
        requestedLevel = null
    }

    /** Animates a newly revealed sheet from Hidden to Mini, then drops the Hidden anchor. */
    internal suspend fun reveal() {
        try {
            draggable.animateTo(PlayerLevel.Mini, animationSpec)
        } finally {
            revealPending = false
            sync(PlayerLevel.Mini)
        }
    }

    /** Animates a sheet whose queue emptied down to Hidden, then drops the other anchors. */
    internal suspend fun collapse() {
        try {
            draggable.animateTo(PlayerLevel.Hidden, animationSpec)
        } finally {
            collapsePending = false
            sync(draggable.targetValue)
        }
    }

    /** Moves to [level] if it is allowed: animated on a sheet, a snap in the pane. */
    suspend fun moveTo(level: PlayerLevel) {
        if (level !in allowedLevels) return
        if (mode == PlayerMode.Pane) draggable.snapTo(level) else draggable.animateTo(level, animationSpec)
    }

    /** Steps one level down for back. Returns false when back belongs to the destinations. */
    suspend fun stepDown(): Boolean {
        val lower = settledLevel.stepDown() ?: return false
        moveTo(lower)
        return true
    }

    /** Drags the sheet with a nested-scroll delta. Returns the delta consumed. */
    internal fun dispatchRawDelta(delta: Float): Float = draggable.dispatchRawDelta(delta)

    internal val minOffset: Float get() = draggable.anchors.minPosition()

    /** Flings the sheet to an anchor with [flingBehavior], returning the velocity consumed (M3's sheet pattern). */
    internal suspend fun fling(
        flingBehavior: FlingBehavior,
        velocity: Float,
    ): Float {
        var consumed = 0f
        draggable.anchoredDrag {
            val scope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val from = offset
                    val to = (from + pixels).coerceIn(draggable.anchors.minPosition(), draggable.anchors.maxPosition())
                    dragTo(to)
                    return to - from
                }
            }
            consumed = with(flingBehavior) { scope.performFling(velocity) }
        }
        return consumed
    }

    companion object {
        /**
         * Stand-in anchors before the first layout and in the pane, where no gesture drives the offset.
         * It rests partway, so a restored Expanded level stands until the sheet is measured.
         */
        internal val NominalGeometry = PlayerSheetGeometry(height = 4f, navBarHeight = 0f, miniHeight = 1f, restOffset = 1f)
    }
}

@Composable
fun rememberPlayerSheetState(
    initialMode: PlayerMode,
    animationSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec(),
): PlayerSheetState {
    val draggable = rememberSaveable(saver = AnchoredDraggableState.Saver()) { AnchoredDraggableState(PlayerLevel.Hidden) }
    val mode = rememberSaveable { mutableStateOf(initialMode) }
    return remember(draggable, mode) { PlayerSheetState(draggable, mode, animationSpec) }.also { it.animationSpec = animationSpec }
}
