package com.simplecityapps.shuttle.ui.widgets

import androidx.compose.ui.unit.Dp
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

    @Test
    fun `glance allows at most sixteen responsive sizes`() {
        (widgetBreakpoints.size <= 16) shouldBe true
    }

    @Test
    fun `every breakpoint keeps play pause and next, in order, and fits its buttons`() {
        widgetBreakpoints.forEach { size ->
            val layout = widgetLayoutFor(size)
            layout.buttons.containsAll(listOf(PlayPause, Next)) shouldBe true
            layout.buttons shouldBe allButtons.filter { it in layout.buttons }
            val inner = size.width - layout.padding * 2
            (WidgetDimens.buttonSize * layout.buttons.size <= inner) shouldBe true
        }
    }

    @Test
    fun `artwork always fits inside the widget`() {
        widgetBreakpoints.forEach { size ->
            val layout = widgetLayoutFor(size)
            if (layout.showArt) {
                (layout.artSize <= size.height - layout.padding * 2) shouldBe true
                (layout.artSize <= size.width - layout.padding * 2) shouldBe true
            }
        }
    }

    @Test
    fun `one row tall is a single line with art filling the height, then text, then buttons`() {
        val layout = widgetLayoutFor(DpSize(340.dp, 72.dp))
        layout.mode shouldBe WidgetMode.Row
        layout.artSize shouldBe 56.dp
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
    fun `a two cell card keeps its art and gives up previous`() {
        val layout = widgetLayoutFor(DpSize(180.dp, 96.dp))
        layout.mode shouldBe WidgetMode.Card
        layout.showArt shouldBe true
        layout.buttons shouldBe listOf(PlayPause, Next)
    }

    @Test
    fun `two rows tall puts the art beside the text and controls`() {
        val layout = widgetLayoutFor(DpSize(250.dp, 96.dp))
        layout.mode shouldBe WidgetMode.Card
        layout.showArt shouldBe true
        layout.buttons shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `a large wide widget has big art, three lines and the full button row`() {
        val layout = widgetLayoutFor(DpSize(340.dp, 240.dp))
        layout.mode shouldBe WidgetMode.Large
        layout.artSize shouldBe WidgetDimens.maxArt
        layout.textLines shouldBe 3
        layout.buttons shouldBe allButtons
    }

    @Test
    fun `a two by two widget keeps its art beside the title and artist`() {
        val layout = widgetLayoutFor(DpSize(180.dp, 170.dp))
        layout.mode shouldBe WidgetMode.Large
        layout.showArt shouldBe true
        layout.textLines shouldBe 2
        layout.buttons shouldBe listOf(Previous, PlayPause, Next)
    }

    @Test
    fun `a tall narrow widget stacks art above the text`() {
        val layout = widgetLayoutFor(DpSize(180.dp, 240.dp))
        layout.mode shouldBe WidgetMode.Tile
        layout.showArt shouldBe true
    }

    @Test
    fun `saved artwork is as large as the biggest layout draws it`() {
        maxWidgetArtSize shouldBe widgetBreakpoints.map { widgetLayoutFor(it).artSize }.filter { it != Dp.Unspecified }.maxOf { it }
    }
}
