package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.search.SearchHit

/** One line of a result row: its parts joined with " · ", and the [matches] (indices into [text]) to show in bold. */
data class HighlightedLine(val text: String, val matches: List<IntRange>)

private const val PartSeparator = " · "

/**
 * Each of a row's [lines] with the query's matches located in its joined text. The lines are highlighted together, so a
 * query token is bolded only where it matches best in the row.
 */
fun highlightedLines(hit: SearchHit<*>, lines: List<List<String>>): List<HighlightedLine> {
    val ranges = hit.highlights(lines.flatten()).iterator()
    return lines.map { parts ->
        val text = StringBuilder()
        val matches = mutableListOf<IntRange>()
        parts.forEachIndexed { i, part ->
            if (i > 0) text.append(PartSeparator)
            val start = text.length
            text.append(part)
            ranges.next().forEach { matches += (start + it.first)..(start + it.last) }
        }
        HighlightedLine(text.toString(), matches)
    }
}
