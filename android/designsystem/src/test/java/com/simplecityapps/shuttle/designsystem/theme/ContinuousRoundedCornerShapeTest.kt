package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContinuousRoundedCornerShapeTest {

    private val density = Density(1f)

    private fun ContinuousRoundedCornerShape.bounds(size: Size): Rect {
        val outline = createOutline(size, LayoutDirection.Ltr, density)
        return outline.shouldBeInstanceOf<Outline.Generic>().path.getBounds()
    }

    @Test
    fun `a radius past half the shorter side clamps to it with no smoothing left, a capsule`() {
        val corner = continuousCorner(radius = 500f, smoothing = DefaultCornerSmoothing, budget = 50f)

        corner.radius shouldBe 50f
        corner.smoothing shouldBe 0f
        corner.p shouldBe 50f
        corner.arcSweep shouldBe 90f
    }

    @Test
    fun `a small radius keeps its smoothing and spans (1 + smoothing) times the radius`() {
        val corner = continuousCorner(radius = 20f, smoothing = 0.6f, budget = 100f)

        corner.radius shouldBe 20f
        corner.smoothing shouldBe 0.6f
        corner.p shouldBe (32f plusOrMinus 0.001f)
        corner.arcSweep shouldBe (36f plusOrMinus 0.001f)
    }

    @Test
    fun `smoothing shrinks to what the budget leaves room for`() {
        val corner = continuousCorner(radius = 40f, smoothing = 0.6f, budget = 50f)

        corner.smoothing shouldBe (0.25f plusOrMinus 0.001f)
        corner.p shouldBe (50f plusOrMinus 0.001f)
    }

    @Test
    fun `the path's bounds are the size`() {
        val size = Size(300f, 180f)

        ContinuousRoundedCornerShape(24.dp).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
        ContinuousRoundedCornerShape(50).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
        ContinuousRoundedCornerShape(topStart = 28.dp, topEnd = 28.dp).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
    }

    @Test
    fun `no corners is a plain rectangle`() {
        ContinuousRoundedCornerShape(0.dp).createOutline(Size(10f, 10f), LayoutDirection.Ltr, density)
            .shouldBeInstanceOf<Outline.Rectangle>()
    }

    @Test
    fun `the outline is reused for the same size`() {
        val shape = ContinuousRoundedCornerShape(12.dp)
        val first = shape.createOutline(Size(100f, 100f), LayoutDirection.Ltr, density)

        shape.createOutline(Size(100f, 100f), LayoutDirection.Ltr, density) shouldBeSameInstanceAs first
    }

    @Test
    fun `lerps towards a RoundedCornerShape as a continuous shape`() {
        val halfway = ContinuousRoundedCornerShape(0.dp).lerp(RoundedCornerShape(20.dp), 0.5f)

        val shape = halfway.shouldBeInstanceOf<ContinuousRoundedCornerShape>()
        shape.topStart.toPx(Size(100f, 100f), density) shouldBe 10f
    }
}
