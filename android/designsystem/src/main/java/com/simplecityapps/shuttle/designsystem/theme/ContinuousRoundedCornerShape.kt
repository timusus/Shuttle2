package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Figma's iOS-like corner smoothing: 0 is a plain circular corner, 1 the smoothest. */
const val DefaultCornerSmoothing = 0.6f

/**
 * A rounded rectangle with continuous corners: each corner eases from the straight edge into its
 * arc through a pair of cubics, so curvature ramps up instead of jumping (the G2-style "squircle"
 * of iOS and Android 16's Settings), following Figma's corner-smoothing construction
 * (https://www.figma.com/blog/desperately-seeking-squircles/).
 *
 * Corners take [CornerSize]s like `RoundedCornerShape`. Each radius clamps to half the shorter
 * side, and the smoothing shrinks where a corner has no room for it, so a full radius gives a
 * capsule. Being a [CornerBasedShape] it fits the `Shapes` scale, and it lerps with itself and
 * with `RoundedCornerShape` so Material's press and toggle shape animations still run.
 */
class ContinuousRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = DefaultCornerSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    // The last outline built. Theme shapes are shared by every cell of a grid, which mostly share a size.
    private var cached: CachedOutline? = null

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd
        cached?.let { if (it.matches(size, topLeft, topRight, bottomRight, bottomLeft)) return it.outline }
        val outline = Outline.Generic(continuousRoundedRectPath(size, topLeft, topRight, bottomRight, bottomLeft, smoothing))
        cached = CachedOutline(size, topLeft, topRight, bottomRight, bottomLeft, outline)
        return outline
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) = ContinuousRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    /** Lerps the corner sizes (and smoothing) towards another continuous or rounded-corner shape. */
    override fun lerp(other: Any?, t: Float): Any? {
        val target = when (other) {
            is ContinuousRoundedCornerShape -> other
            is RoundedCornerShape -> other.continuous(smoothing)
            RectangleShape -> ContinuousRoundedCornerShape(ZeroCornerSize, ZeroCornerSize, ZeroCornerSize, ZeroCornerSize, smoothing)
            else -> return null
        }
        return ContinuousRoundedCornerShape(
            topStart = lerp(topStart, target.topStart, t),
            topEnd = lerp(topEnd, target.topEnd, t),
            bottomEnd = lerp(bottomEnd, target.bottomEnd, t),
            bottomStart = lerp(bottomStart, target.bottomStart, t),
            smoothing = smoothing + (target.smoothing - smoothing) * t,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousRoundedCornerShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString() = "ContinuousRoundedCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = $bottomEnd, " +
        "bottomStart = $bottomStart, smoothing = $smoothing)"
}

/** A [ContinuousRoundedCornerShape] with all four corners at [corner]. */
fun ContinuousRoundedCornerShape(corner: CornerSize, smoothing: Float = DefaultCornerSmoothing) = ContinuousRoundedCornerShape(corner, corner, corner, corner, smoothing)

/** A [ContinuousRoundedCornerShape] with all four corners at [size]. */
fun ContinuousRoundedCornerShape(size: Dp, smoothing: Float = DefaultCornerSmoothing): ContinuousRoundedCornerShape = CornerSize(size).let { ContinuousRoundedCornerShape(it, it, it, it, smoothing) }

/** A [ContinuousRoundedCornerShape] with all four corners at [percent] of the shorter side; 50 is a capsule. */
fun ContinuousRoundedCornerShape(percent: Int, smoothing: Float = DefaultCornerSmoothing): ContinuousRoundedCornerShape = CornerSize(percent).let { ContinuousRoundedCornerShape(it, it, it, it, smoothing) }

/** A [ContinuousRoundedCornerShape] with a size per corner. */
fun ContinuousRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    smoothing: Float = DefaultCornerSmoothing,
) = ContinuousRoundedCornerShape(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

/** A [ContinuousRoundedCornerShape] with a size in pixels per corner. */
fun ContinuousRoundedCornerShape(
    topStart: Float = 0f,
    topEnd: Float = 0f,
    bottomEnd: Float = 0f,
    bottomStart: Float = 0f,
    smoothing: Float = DefaultCornerSmoothing,
) = ContinuousRoundedCornerShape(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

private fun CornerBasedShape.continuous(smoothing: Float) = ContinuousRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

private fun lerp(start: CornerSize, stop: CornerSize, t: Float): CornerSize = when {
    t <= 0f || start == stop -> start
    t >= 1f -> stop
    else -> LerpCornerSize(start, stop, t)
}

private data class LerpCornerSize(val start: CornerSize, val stop: CornerSize, val t: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val from = start.toPx(shapeSize, density)
        return from + (stop.toPx(shapeSize, density) - from) * t
    }
}

private class CachedOutline(
    val size: Size,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val outline: Outline,
) {
    fun matches(size: Size, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) = this.size == size &&
        this.topLeft == topLeft &&
        this.topRight == topRight &&
        this.bottomRight == bottomRight &&
        this.bottomLeft == bottomLeft
}

/**
 * One corner's measurements in Figma's construction: the corner spans [p] along each edge; a
 * cubic with handles [a] and [b] runs from there to the circular arc of [radius], which subtends
 * [arcSweep] degrees; [c] and [d] place the arc's ends.
 */
internal class ContinuousCorner(
    val radius: Float,
    val smoothing: Float,
    val p: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
) {
    /** Half the angle the smoothing takes off each end of the arc. */
    val alpha: Float get() = 45f * smoothing
    val arcSweep: Float get() = 90f - 2 * alpha
}

/**
 * A corner of [radius] with [smoothing], given [budget] (half the shorter side) to fit in: the
 * radius clamps to the budget, and the smoothing drops to what the budget leaves room for.
 */
internal fun continuousCorner(radius: Float, smoothing: Float, budget: Float): ContinuousCorner {
    val r = radius.coerceIn(0f, budget)
    if (r <= 0f) return ContinuousCorner(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    val s = min(smoothing, budget / r - 1f).coerceIn(0f, 1f)
    val p = min((1 + s) * r, budget)
    val arcSweep = 90f * (1 - s)
    val arcSectionLength = sin(toRadians(arcSweep / 2)) * r * sqrt(2f)
    val alpha = (90f - arcSweep) / 2
    val p3ToP4 = r * tan(toRadians(alpha / 2))
    val beta = 45f * s
    val c = p3ToP4 * cos(toRadians(beta))
    val d = c * tan(toRadians(beta))
    val b = (p - arcSectionLength - c - d) / 3
    return ContinuousCorner(r, s, p, 2 * b, b, c, d)
}

private fun toRadians(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)

/** The outline of a [size] rectangle with continuous corners, clockwise from the top-left corner's end. */
internal fun continuousRoundedRectPath(
    size: Size,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
): Path {
    val budget = size.minDimension / 2
    val w = size.width
    val h = size.height
    val tl = continuousCorner(topLeft, smoothing, budget)
    return Path().apply {
        moveTo(tl.p, 0f)
        corner(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f), -90f, continuousCorner(topRight, smoothing, budget))
        corner(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f), 0f, continuousCorner(bottomRight, smoothing, budget))
        corner(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f), 90f, continuousCorner(bottomLeft, smoothing, budget))
        corner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), 180f, tl)
        close()
    }
}

/**
 * Draws the corner at [vertex], arriving along direction [inward] and leaving along [outward]: a
 * line to the corner's start, a cubic into the arc, the arc (starting at [baseAngle] plus the
 * smoothing's share, in degrees), and a cubic back out to the next edge.
 */
private fun Path.corner(vertex: Offset, inward: Offset, outward: Offset, baseAngle: Float, k: ContinuousCorner) {
    if (k.radius == 0f) {
        lineTo(vertex.x, vertex.y)
        return
    }
    fun at(along: Float, across: Float) = vertex - inward * along + outward * across
    val start = at(k.p, 0f)
    lineTo(start.x, start.y)
    val c1 = at(k.p - k.a, 0f)
    val c2 = at(k.p - k.a - k.b, 0f)
    val arcStart = at(k.p - k.a - k.b - k.c, k.d)
    cubicTo(c1.x, c1.y, c2.x, c2.y, arcStart.x, arcStart.y)
    val center = at(k.radius, k.radius)
    arcTo(Rect(center, k.radius), baseAngle + k.alpha, k.arcSweep, forceMoveTo = false)
    val c3 = at(0f, k.p - k.a - k.b)
    val c4 = at(0f, k.p - k.a)
    val end = at(0f, k.p)
    cubicTo(c3.x, c3.y, c4.x, c4.y, end.x, end.y)
}
