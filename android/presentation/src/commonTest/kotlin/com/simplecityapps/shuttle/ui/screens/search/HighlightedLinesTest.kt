package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HighlightedLinesTest {
    private fun hit(query: String) = SearchHit(Unit, SearchQuery.parse(query))

    @Test
    fun `a name is bold where the query matched it`() {
        highlightedLines(hit("night"), listOf(listOf("Nightjar & the Loom"))) shouldBe
            listOf(HighlightedLine("Nightjar & the Loom", listOf(0..4)))
    }

    @Test
    fun `a line's parts are joined and later matches are offset past the separator`() {
        highlightedLines(hit("loom"), listOf(listOf("Nightjar", "Loom Works"))) shouldBe
            listOf(HighlightedLine("Nightjar · Loom Works", listOf(11..14)))
    }

    @Test
    fun `lines are highlighted together so a token is bold only where it matches best`() {
        highlightedLines(hit("night lights"), listOf(listOf("Lights Out"), listOf("Night Bus Frequencies"))) shouldBe
            listOf(
                HighlightedLine("Lights Out", listOf(0..5)),
                HighlightedLine("Night Bus Frequencies", listOf(0..4))
            )
    }

    @Test
    fun `text the query did not match has no bold`() {
        highlightedLines(hit("zzz"), listOf(listOf("Nightjar"))) shouldBe listOf(HighlightedLine("Nightjar", emptyList()))
    }
}
