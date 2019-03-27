package com.simplecityapps.shuttle.ui.common.recyclerview

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.shuttle.ui.common.utils.dp

class SpacesItemDecoration(space: Int) : RecyclerView.ItemDecoration() {

    private val space: Int = space.dp

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