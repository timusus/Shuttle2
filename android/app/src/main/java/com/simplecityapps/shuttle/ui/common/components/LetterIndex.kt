package com.simplecityapps.shuttle.ui.common.components

import java.text.Normalizer
import kotlin.math.roundToInt

/** A run of items whose sort key starts with [letter], beginning at item [firstIndex]. */
data class LetterSection(val letter: String, val firstIndex: Int)

/** The section for keys that don't start with a letter of a short alphabet: digits, symbols, blanks, ideographs. */
private const val OtherLetter = "#"

/** Scripts whose letters are few enough to index one by one. */
private val AlphabetScripts = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC)

/**
 * The letter [key] files under: its first non-blank character, uppercased with any accent dropped (É under E), or "#"
 * for anything that isn't a Latin, Greek or Cyrillic letter.
 */
fun letterLabel(key: String?): String {
    val first = key?.firstOrNull { !it.isWhitespace() } ?: return OtherLetter
    if (!first.isLetter() || Character.UnicodeScript.of(first.code) !in AlphabetScripts) return OtherLetter
    val base = if (first.code < 128) first else Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
    return base.uppercaseChar().toString()
}

/**
 * [items]' letter sections in list order: a new section wherever the letter of [key] changes. The list is already
 * sorted, so this is one pass; a key the sort compares keeps each letter's items together.
 */
fun <T> letterSections(items: List<T>, key: (T) -> String?): List<LetterSection> {
    val sections = mutableListOf<LetterSection>()
    items.forEachIndexed { index, item ->
        val letter = letterLabel(key(item))
        if (sections.lastOrNull()?.letter != letter) sections += LetterSection(letter, index)
    }
    return sections
}

/** The index of the section item [itemIndex] falls in; items before the first section fall in it. */
fun sectionIndexOf(sections: List<LetterSection>, itemIndex: Int): Int {
    val found = sections.binarySearch { it.firstIndex.compareTo(itemIndex) }
    return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
}

/** The section at [fraction] of the way down a track that spaces [sectionCount] sections evenly. */
fun sectionIndexAt(sectionCount: Int, fraction: Float): Int = (fraction.coerceIn(0f, 1f) * (sectionCount - 1).coerceAtLeast(0)).roundToInt()

/** Where section [sectionIndex] sits along the track, from 0 (top) to 1 (bottom). */
fun sectionFraction(sectionIndex: Int, sectionCount: Int): Float = if (sectionCount < 2) 0f else sectionIndex.toFloat() / (sectionCount - 1)
