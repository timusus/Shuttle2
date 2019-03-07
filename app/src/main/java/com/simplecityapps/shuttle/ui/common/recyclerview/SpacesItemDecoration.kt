package com.simplecityapps.shuttle.ui.common.recyclerview

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacesItemDecoration(space: Int) : RecyclerView.ItemDecoration() {

    private val space: Int = space.px

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {

        if (parent.getChildLayoutPosition(view) == 0) {
            outRect.left = 0
            outRect.right = space
        } else {
            outRect.left = space
            outRect.right = space
        }
    }
}

val Int.dp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

val Int.px: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()