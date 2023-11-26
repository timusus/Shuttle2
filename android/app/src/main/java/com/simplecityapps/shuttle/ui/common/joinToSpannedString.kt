package com.simplecityapps.shuttle.ui.common

import android.text.SpannableStringBuilder
import android.text.SpannedString

fun <T> Iterable<T>.joinToSpannedString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): SpannedString {
    return SpannedString(joinTo(SpannableStringBuilder(), separator, prefix, postfix, limit, truncated, transform))
}
