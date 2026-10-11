package com.simplecityapps.shuttle.ui.text

import android.content.res.Resources
import android.text.format.DateUtils
import com.simplecityapps.shuttle.R

/**
 * When [thenMillis] was, as "Just now" under a minute, otherwise the platform's "2 hours ago" or "Yesterday". A time
 * after [nowMillis] reads as just now.
 */
fun Resources.relativeTimeSpan(
    thenMillis: Long,
    nowMillis: Long,
): String {
    if (nowMillis - thenMillis < DateUtils.MINUTE_IN_MILLIS) return getString(R.string.relative_time_just_now)
    return DateUtils.getRelativeTimeSpanString(thenMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()
}
