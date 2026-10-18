package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
    fun `the path's bounds are the size`() {
        val size = Size(300f, 180f)

        ContinuousRoundedCornerShape(24.dp).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
        ContinuousRoundedCornerShape(50).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
        ContinuousRoundedCornerShape(topStart = 28.dp, topEnd = 28.dp).bounds(size) shouldBe Rect(0f, 0f, 300f, 180f)
    }

    @Test
    fun `the outline is reused for the same size`() {
        val shape = ContinuousRoundedCornerShape(12.dp)
        val first = shape.createOutline(Size(100f, 100f), LayoutDirection.Ltr, density)

        shape.createOutline(Size(100f, 100f), LayoutDirection.Ltr, density) shouldBeSameInstanceAs first
    }
}
