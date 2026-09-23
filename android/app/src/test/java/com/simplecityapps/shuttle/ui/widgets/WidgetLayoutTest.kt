package com.simplecityapps.shuttle.ui.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.ui.widgets.WidgetButton.Next
import com.simplecityapps.shuttle.ui.widgets.WidgetButton.PlayPause
import com.simplecityapps.shuttle.ui.widgets.WidgetButton.Previous
import com.simplecityapps.shuttle.ui.widgets.WidgetButton.Repeat
import com.simplecityapps.shuttle.ui.widgets.WidgetButton.Shuffle
import io.kotest.matchers.shouldBe
import org.junit.Test

class WidgetLayoutTest {
    private val allButtons = listOf(Shuffle, Previous, PlayPause, Next, Repeat)

    @Test
    fun `wide widgets show every control with play pause in the middle`() {
        buttonsFor(240.dp) shouldBe allButtons
    }

    @Test
    fun `shuffle and repeat go first as the widget narrows`() {
        buttonsFor(239.dp) shouldBe listOf(Previous, PlayPause, Next)
        buttonsFor(144.dp) shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `previous goes next, leaving play pause and next`() {
        buttonsFor(143.dp) shouldBe listOf(PlayPause, Next)
        buttonsFor(96.dp) shouldBe listOf(PlayPause, Next)
    }

    @Test
    fun `play pause and next remain even when nothing fits`() {
        buttonsFor(0.dp) shouldBe listOf(PlayPause, Next)
    }

    /** Sizes from one row to four, two cells wide to full width, on dense and roomy launcher grids. */
    private val sampleSizes: List<DpSize> =
        listOf(120.dp, 170.dp, 250.dp, 291.dp, 340.dp, 388.dp)
            .flatMap { width -> listOf(64.dp, 80.dp, 100.dp, 130.dp, 170.dp, 212.dp, 250.dp, 318.dp, 430.dp).map { height -> DpSize(width, height) } }

    @Test
    fun `every size keeps play pause and next, in order, and fits its buttons`() {
        sampleSizes.forEach { size ->
            val layout = widgetLayoutFor(size)
            layout.buttons.containsAll(listOf(PlayPause, Next)) shouldBe true
            layout.buttons shouldBe allButtons.filter { it in layout.buttons }
            val inner = size.width - layout.padding * 2
            (WidgetDimens.buttonSize * layout.buttons.size <= inner) shouldBe true
        }
    }

    @Test
    fun `artwork always fits inside the padding`() {
        sampleSizes.forEach { size ->
            val layout = widgetLayoutFor(size)
            if (layout.showArt) {
                (layout.art.height <= size.height - layout.padding * 2) shouldBe true
                (layout.art.width <= size.width - layout.padding * 2) shouldBe true
            }
        }
    }

    @Test
    fun `artwork beside the text fills the height, so its top gap equals its side gap`() {
        sampleSizes.map { widgetLayoutFor(it) to it }
            .filter { (layout, _) -> layout.showArt && layout.mode in listOf(WidgetMode.Row, WidgetMode.Card) }
            .forEach { (layout, size) -> layout.art.height shouldBe size.height - layout.padding * 2 }
    }

    @Test
    fun `split artwork fills the height above the button row`() {
        val size = DpSize(388.dp, 212.dp)
        val layout = widgetLayoutFor(size)
        layout.mode shouldBe WidgetMode.Split
        layout.art.height shouldBe size.height - layout.padding * 2 - WidgetDimens.buttonSize - WidgetDimens.gap
        layout.art.width shouldBe layout.art.height
    }

    @Test
    fun `hero artwork fills the widget inside the padding`() {
        val size = DpSize(388.dp, 318.dp)
        val layout = widgetLayoutFor(size)
        layout.mode shouldBe WidgetMode.Hero
        layout.art shouldBe DpSize(size.width - layout.padding * 2, size.height - layout.padding * 2)
    }

    @Test
    fun `padding is the one token wherever a button fits around it`() {
        sampleSizes.filter { it.height >= WidgetDimens.buttonSize + WidgetDimens.padding * 2 }.forEach { size ->
            widgetLayoutFor(size).padding shouldBe WidgetDimens.padding
        }
    }

    @Test
    fun `padding shrinks on rows too short for it, but never below the minimum`() {
        widgetPadding(64.dp) shouldBe 8.dp
        widgetPadding(68.dp) shouldBe 10.dp
        widgetPadding(40.dp) shouldBe WidgetDimens.minPadding
        widgetPadding(100.dp) shouldBe WidgetDimens.padding
    }

    @Test
    fun `artwork corners are concentric with the widget's`() {
        innerCornerRadius(outerRadius = 28.dp, padding = 12.dp) shouldBe 16.dp
        innerCornerRadius(outerRadius = 20.dp, padding = 12.dp) shouldBe 8.dp
    }

    @Test
    fun `artwork corners stay rounded when the widget's radius is small`() {
        innerCornerRadius(outerRadius = 8.dp, padding = 12.dp) shouldBe WidgetDimens.minInnerRadius
    }

    @Test
    fun `background opacity maps the percentage to alpha`() {
        widgetBackgroundAlpha(100) shouldBe 1f
        widgetBackgroundAlpha(50) shouldBe 0.5f
        widgetBackgroundAlpha(0) shouldBe 0f
    }

    @Test
    fun `background opacity outside the range is clamped`() {
        widgetBackgroundAlpha(150) shouldBe 1f
        widgetBackgroundAlpha(-10) shouldBe 0f
    }

    @Test
    fun `one row tall is a single line with art filling the height, then text, then buttons`() {
        val layout = widgetLayoutFor(DpSize(340.dp, 80.dp))
        layout.mode shouldBe WidgetMode.Row
        layout.art shouldBe DpSize(56.dp, 56.dp)
        layout.textLines shouldBe 2
        layout.buttons shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `a narrow single row drops the art before the buttons`() {
        val layout = widgetLayoutFor(DpSize(180.dp, 48.dp))
        layout.mode shouldBe WidgetMode.Row
        layout.showArt shouldBe false
        layout.textLines shouldBe 2
        layout.buttons shouldBe listOf(PlayPause, Next)
    }

    @Test
    fun `the narrowest single row shows just the buttons`() {
        val layout = widgetLayoutFor(DpSize(120.dp, 48.dp))
        layout.textLines shouldBe 0
        layout.buttons shouldBe listOf(PlayPause, Next)
    }

    @Test
    fun `a short two row widget puts text over the buttons beside the art`() {
        val layout = widgetLayoutFor(DpSize(340.dp, 130.dp))
        layout.mode shouldBe WidgetMode.Card
        layout.art shouldBe DpSize(106.dp, 106.dp)
        layout.buttons shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `a narrow card drops the art rather than shrinking it off the edges`() {
        val layout = widgetLayoutFor(DpSize(170.dp, 130.dp))
        layout.mode shouldBe WidgetMode.Card
        layout.showArt shouldBe false
    }

    @Test
    fun `a four by two widget has art above the full button row with text beside it`() {
        val layout = widgetLayoutFor(DpSize(388.dp, 212.dp))
        layout.mode shouldBe WidgetMode.Split
        layout.art shouldBe DpSize(132.dp, 132.dp)
        layout.textLines shouldBe 3
        layout.titleLines shouldBe 2
        layout.buttons shouldBe allButtons
    }

    @Test
    fun `a two by two widget is too narrow for text beside the art, so the art fills it`() {
        val layout = widgetLayoutFor(DpSize(170.dp, 212.dp))
        layout.mode shouldBe WidgetMode.Hero
        layout.buttons shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `three rows and up fill the widget with the art`() {
        listOf(DpSize(388.dp, 318.dp), DpSize(388.dp, 430.dp), DpSize(170.dp, 318.dp)).forEach { size ->
            widgetLayoutFor(size).mode shouldBe WidgetMode.Hero
        }
        widgetLayoutFor(DpSize(388.dp, 430.dp)).buttons shouldBe allButtons
    }

    @Test
    fun `saved artwork covers the largest phone layout`() {
        (maxWidgetArtSize >= sampleSizes.maxOf { size -> widgetLayoutFor(size).art.takeIf { it != DpSize.Unspecified }?.let { maxOf(it.width, it.height) } ?: 0.dp }) shouldBe true
    }
}
