package com.simplecityapps.shuttle.ui.common.error

import android.content.res.Resources
import com.simplecityapps.shuttle.R

fun Error.userDescription(resources: Resources): String {
    return when (this) {
        is UserFriendlyError -> message
        is UnexpectedError -> resources.getString(R.string.error_unexpected)
        else -> resources.getString(R.string.error_unknown)
    }
}