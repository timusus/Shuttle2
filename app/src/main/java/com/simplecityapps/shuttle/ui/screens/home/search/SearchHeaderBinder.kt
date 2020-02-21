package com.simplecityapps.shuttle.ui.screens.home.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SearchHeaderBinder(
    val title: String
) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_search_header, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.SearchHeader
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchHeaderBinder

        if (title != other.title) return false

        return true
    }

    override fun hashCode(): Int {
        return title.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SearchHeaderBinder>(itemView) {

        override fun bind(viewBinder: SearchHeaderBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            itemView as TextView
            itemView.text = viewBinder.title
        }
    }
}