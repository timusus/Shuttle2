package com.simplecityapps.shuttle.ui.common.view

import android.view.View
import android.view.ViewGroup

fun View.setMargins(
    leftMargin: Int = (layoutParams as?  ViewGroup.MarginLayoutParams)?.leftMargin ?: 0,
    topMargin: Int = (layoutParams as?  ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
    rightMargin: Int = (layoutParams as?  ViewGroup.MarginLayoutParams)?.rightMargin ?: 0,
    bottomMargin: Int = (layoutParams as?  ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
) {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
        layoutParams.setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
    }
}