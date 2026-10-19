package com.simplecityapps.shuttle.ui.screens.settings

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

/** A signed gain such as "+1.5 dB", with the decimal separator of [locale]. */
fun formatDecibels(value: Float, locale: Locale): String = String.format(locale, "%+.1f dB", value)

fun formatPercent(percent: Int): String = "$percent%"

/** The short date and time of the last library scan, in [locale] and [timeZone]. */
fun formatLastScan(instant: Instant, locale: Locale, timeZone: TimeZone): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    .apply { this.timeZone = timeZone }
    .format(Date(instant.toEpochMilliseconds()))
