package com.simplecityapps.shuttle.ui.common

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.res.getColorOrThrow
import androidx.core.content.res.use

fun Context.getAttrColor(
    @AttrRes attrResId: Int
): Int {
    val typedValue = TypedValue()
    obtainStyledAttributes(typedValue.data, intArrayOf(attrResId)).use { typedArray ->
        return typedArray.getColorOrThrow(0)
    }
}
