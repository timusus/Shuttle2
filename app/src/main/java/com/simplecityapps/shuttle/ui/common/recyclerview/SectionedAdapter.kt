package com.simplecityapps.shuttle.ui.common.recyclerview

import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView

open class SectionedAdapter : RecyclerAdapter(), FastScrollRecyclerView.SectionedAdapter {

    override fun getSectionName(position: Int): String {
        return getSectionName(items.getOrNull(position))
    }

    open fun getSectionName(viewBinder: ViewBinder?): String {
        return (viewBinder as? SectionViewBinder)?.getSectionName() ?: ""
    }
}

interface SectionViewBinder : ViewBinder {
    fun getSectionName(): String? = null
}