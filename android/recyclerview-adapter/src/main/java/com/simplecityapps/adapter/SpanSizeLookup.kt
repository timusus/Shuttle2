package com.simplecityapps.adapter

import androidx.recyclerview.widget.GridLayoutManager

class SpanSizeLookup(
    private val viewModelAdapter: RecyclerAdapter,
    private var spanCount: Int
) : GridLayoutManager.SpanSizeLookup() {
    fun setSpanCount(spanCount: Int) {
        this.spanCount = spanCount
    }

    override fun getSpanSize(position: Int): Int = viewModelAdapter.items.getOrNull(position)?.spanSize(spanCount) ?: 1
}
