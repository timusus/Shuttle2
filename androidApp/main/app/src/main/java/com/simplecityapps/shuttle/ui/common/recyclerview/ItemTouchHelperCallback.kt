package com.simplecityapps.shuttle.ui.common.recyclerview

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter

open class ItemTouchHelperCallback(
    private val onItemMoveListener: OnItemMoveListener
) : ItemTouchHelper.Callback() {

    interface OnItemMoveListener {
        fun onItemMoved(from: Int, to: Int)
    }

    private var startPosition = -1
    private var endPosition = -1

    override fun isLongPressDragEnabled(): Boolean {
        // Long presses are handled separately
        return false
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (startPosition == -1) {
            startPosition = viewHolder.adapterPosition
        }
        endPosition = target.adapterPosition

        (recyclerView.adapter as RecyclerAdapter).move(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (startPosition != -1 && endPosition != -1) {
            onItemMoveListener.onItemMoved(startPosition, endPosition)
        }

        startPosition = -1
        endPosition = -1
    }
}