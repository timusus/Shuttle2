package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.sorting.LetterSection

/** Derived from the library and sort alone, so an import progress tick reuses the sort and index. */
internal class IndexedList<T, S>(val items: List<T>, val sortOrder: S, val letterIndex: List<LetterSection>?)
