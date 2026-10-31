package com.simplecityapps.shuttle.model

import kotlinx.datetime.LocalDate

/** Tags use 0 (or a stray 1) for "no year"; anything before 1000 is not a real release year. */
fun Int.asTagYear(): Int? = takeIf { it >= MIN_TAG_YEAR }

/** The first of this year as a song's date, or null when the tag year is missing or junk. */
fun Int?.toTagYearDate(): LocalDate? = this?.asTagYear()?.let { LocalDate(it, 1, 1) }

private const val MIN_TAG_YEAR = 1000
