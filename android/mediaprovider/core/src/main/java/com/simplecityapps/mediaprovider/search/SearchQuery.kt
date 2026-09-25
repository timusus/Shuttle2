package com.simplecityapps.mediaprovider.search

import com.simplecityapps.mediaprovider.search.SearchText.isMark
import kotlin.math.min

/**
 * A query, normalised into tokens. Every token matches as a prefix of some field token, within a typo budget that
 * grows with its length ([typoBudget]). Each typo changes the length by at most one, so a token never matches a field
 * token more than its budget shorter than itself: "nightj" finds "Nightjar" and "night", "nightjar" finds only
 * "Nightjar". Equal when their tokens are, so "Inès " and "ines" are the same query.
 */
class SearchQuery private constructor(internal val tokens: List<String>) {
    val isEmpty: Boolean
        get() = tokens.isEmpty()

    /**
     * The ranges of [text] this query matches, merged and in order, so a row can bold them. A field token is
     * highlighted up to the length of the query token's best match, or whole if the query token covers it.
     */
    fun highlights(text: String?): List<IntRange> {
        if (text.isNullOrEmpty() || tokens.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        SearchText.tokens(text, withSources = true) { token, sources, _, _ ->
            val length = tokens.maxOf { query -> PrefixDistance.matchedLength(query, token, typoBudget(query.length)) }
            if (length > 0) {
                var end = sources!![length - 1] + 1
                // Take in the combining marks that trail the last matched char, so an accent isn't left unbolded.
                while (end < text.length && text[end].isMark()) end++
                ranges += sources[0] until end
            }
        }
        return ranges.merged()
    }

    override fun equals(other: Any?): Boolean = other is SearchQuery && other.tokens == tokens

    override fun hashCode(): Int = tokens.hashCode()

    override fun toString(): String = "SearchQuery(${tokens.joinToString(" ")})"

    companion object {
        /** More tokens than this and the rest are ignored; nobody types a query that long. */
        internal const val MAX_TOKENS = 8

        fun parse(query: String): SearchQuery = SearchQuery(SearchText.queryTokens(query).take(MAX_TOKENS))
    }
}

/** How many typos a query token may carry: none up to 3 chars, 1 up to 7, 2 beyond. */
internal fun typoBudget(length: Int): Int = when {
    length <= 3 -> 0
    length <= 7 -> 1
    else -> 2
}

private fun List<IntRange>.merged(): List<IntRange> {
    if (size < 2) return this
    val sorted = sortedBy { it.first }
    val merged = mutableListOf(sorted.first())
    for (range in sorted.drop(1)) {
        val last = merged.last()
        if (range.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, range.last) else merged += range
    }
    return merged
}

/** Bounded Damerau-Levenshtein (optimal string alignment) between a query token and the prefixes of a term. */
internal object PrefixDistance {
    /**
     * The length of the longest prefix of [term] that [query] matches with the fewest typos, if that's within
     * [maxTypos]; 0 when [query] doesn't match any prefix of [term].
     */
    fun matchedLength(query: String, term: String, maxTypos: Int): Int {
        val q = query.length
        var prev2 = IntArray(q + 1)
        var prev = IntArray(q + 1) { it }
        var row = IntArray(q + 1)
        var best = prev[q]
        var bestLength = 0
        for (i in 1..term.length) {
            val c = term[i - 1]
            row[0] = i
            for (j in 1..q) {
                val cost = if (query[j - 1] == c) 0 else 1
                var v = min(min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && c == query[j - 2] && term[i - 2] == query[j - 1]) v = min(v, prev2[j - 2] + 1)
                row[j] = v
            }
            if (row[q] <= best) {
                best = row[q]
                bestLength = i
            }
            if (row.min() > maxTypos && prev.min() > maxTypos) break
            val recycled = prev2
            prev2 = prev
            prev = row
            row = recycled
        }
        return if (best <= maxTypos && bestLength > 0) bestLength else 0
    }
}
