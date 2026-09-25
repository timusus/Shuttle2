package com.simplecityapps.shuttle.ui.screens.home.search

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.simplecityapps.mediaprovider.search.SearchQuery

/** [text] with each part the query matched in [color]. */
internal fun SearchQuery.highlight(text: String, color: Int): CharSequence = SpannableString(text).apply {
    highlights(text).forEach { setSpan(ForegroundColorSpan(color), it.first, it.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
}
