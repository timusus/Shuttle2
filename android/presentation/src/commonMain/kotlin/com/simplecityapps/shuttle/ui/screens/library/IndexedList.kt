package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.sorting.LetterSection

/**
 * A library list in [sortOrder] with its letter index (#627). The list screens derive it from the library and the sort
 * alone, upstream of the import progress and selection they combine it with, so a progress tick reuses the sort and
 * the index rather than redoing both.
 */
internal class IndexedList<T, S>(val items: List<T>, val sortOrder: S, val letterIndex: List<LetterSection>?)
