package com.simplecityapps.shuttle.ui.common.recyclerview;

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.shuttle.ui.common.utils.dp

class GridSpacingItemDecoration(
    space: Int,
    private val includeEdge: Boolean = true
) : RecyclerView.ItemDecoration() {

    private val space: Int = space.dp

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {

        val spanCount = (parent.layoutManager as GridLayoutManager).spanCount
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        if (includeEdge) {
            outRect.top = space / 2
            when (column) {
                0 -> {
                    outRect.left = space * 2
                }
                spanCount - 1 -> {
                    outRect.right = space * 2
                }
                else -> {
                    outRect.left = (space - column * space / spanCount) + (space / spanCount)
                    outRect.right = ((column + 1) * space / spanCount) + (space / spanCount)
                }
            }
            outRect.bottom = space / 2
        } else {
            outRect.left = column * space / spanCount
            outRect.right = space - (column + 1) * space / spanCount
            if (position >= spanCount) {
                outRect.top = space
            }
        }
    }
}