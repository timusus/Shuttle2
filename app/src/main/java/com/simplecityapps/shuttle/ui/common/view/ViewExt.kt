package com.simplecityapps.shuttle.ui.common.view

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.simplecityapps.shuttle.ui.common.utils.dp

fun View.setMargins(
    leftMargin: Int = (layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0,
    topMargin: Int = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0,
    rightMargin: Int = (layoutParams as? ViewGroup.MarginLayoutParams)?.rightMargin ?: 0,
    bottomMargin: Int = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
) {
    (layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
        layoutParams.setMargins(leftMargin, topMargin, rightMargin, bottomMargin)
    }
}

/**
 * Adds a touch delegate to this view's parent, increasing the touch area of the view by [amount] dp in each direction.
 */
fun View.increaseTouchableArea(amount: Int) {
    (parent as? View)?.let { parent ->
        parent.post {
            val touchableArea = Rect()
            getHitRect(touchableArea)
            touchableArea.top -= amount.dp
            touchableArea.bottom += amount.dp
            touchableArea.left -= amount.dp
            touchableArea.right += amount.dp
            parent.touchDelegate = TouchDelegate(touchableArea, this)
        }
    }
}