package com.simplecityapps.diff

import androidx.recyclerview.widget.DiffUtil

class DiffCallbacks(private val oldList: List<ContentsComparator>, private val newList: List<ContentsComparator>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean = oldList[oldItemPosition] == newList[newItemPosition]

    override fun areContentsTheSame(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean = oldList[oldItemPosition].areContentsTheSame(newList[newItemPosition])

    override fun getChangePayload(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Any? = 0
}
