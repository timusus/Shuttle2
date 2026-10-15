package com.simplecityapps.shuttle.ui.common.utils

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/** Formats a length in minutes as "15 min", "1 hr" or "1 hr, 30 min", in the user's locale. */
@Composable
internal fun rememberMinutesFormat(): (Int) -> String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    return remember(locale) {
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        val formatMinutes: (Int) -> String = { minutes ->
            val hours = minutes / 60
            val rest = minutes % 60
            val measures = buildList {
                if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
                if (rest > 0 || hours == 0) add(Measure(rest, MeasureUnit.MINUTE))
            }
            format.formatMeasures(*measures.toTypedArray())
        }
        formatMinutes
    }
}
