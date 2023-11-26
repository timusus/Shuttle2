package com.simplecityapps.diff

import androidx.recyclerview.widget.DiffUtil

class DiffCallbacks(private val oldList: List<ContentsComparator>, private val newList: List<ContentsComparator>) :
    DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }

    override fun areContentsTheSame(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Boolean {
        return oldList[oldItemPosition].areContentsTheSame(newList[newItemPosition])
    }

    override fun getChangePayload(
        oldItemPosition: Int,
        newItemPosition: Int
    ): Any? {
        return 0
    }
}
