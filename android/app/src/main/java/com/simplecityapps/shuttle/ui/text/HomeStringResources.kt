package com.simplecityapps.shuttle.ui.text

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R

@get:StringRes
val HomeString.resId: Int
    get() = when (this) {
        HomeString.HOME_AROUND_THIS_TIME_SUBTITLE -> R.string.home_around_this_time_subtitle
        HomeString.HOME_HEAVY_ROTATION_SUBTITLE -> R.string.home_heavy_rotation_subtitle
        HomeString.HOME_REDISCOVER_SUBTITLE -> R.string.home_rediscover_subtitle
        HomeString.HOME_RECENTLY_ADDED_SUBTITLE -> R.string.home_recently_added_subtitle
        HomeString.HOME_GENRE_PICKS_SUBTITLE -> R.string.home_genre_picks_subtitle
        HomeString.HOME_GENRE_PICKS_LARGEST_SUBTITLE -> R.string.home_genre_picks_largest_subtitle
    }
