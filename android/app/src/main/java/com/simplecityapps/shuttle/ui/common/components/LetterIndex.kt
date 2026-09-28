package com.simplecityapps.shuttle.ui.common.components

import com.simplecityapps.shuttle.sorting.LetterSection
import kotlin.math.roundToInt

// The sections themselves (letterLabel, letterSections) are shared with iOS's section index, in :android:domain.

/** The index of the section item [itemIndex] falls in; items before the first section fall in it. */
fun sectionIndexOf(sections: List<LetterSection>, itemIndex: Int): Int {
    val found = sections.binarySearch { it.firstIndex.compareTo(itemIndex) }
    return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
}

/** The section at [fraction] of the way down a track that spaces [sectionCount] sections evenly. */
fun sectionIndexAt(sectionCount: Int, fraction: Float): Int = (fraction.coerceIn(0f, 1f) * (sectionCount - 1).coerceAtLeast(0)).roundToInt()

/** Where section [sectionIndex] sits along the track, from 0 (top) to 1 (bottom). */
fun sectionFraction(sectionIndex: Int, sectionCount: Int): Float = if (sectionCount < 2) 0f else sectionIndex.toFloat() / (sectionCount - 1)
