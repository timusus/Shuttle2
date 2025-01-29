package com.simplecityapps.shuttle.ui.common.viewbinders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class DiscNumberBinder(
    val label: String
) : ViewBinder {
    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_disc_number, parent, false))

    override fun viewType(): Int = ViewTypes.DiscNumber

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DiscNumberBinder

        if (label != other.label) return false

        return true
    }

    override fun hashCode(): Int = label.hashCode()

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<DiscNumberBinder>(itemView) {
        override fun bind(
            viewBinder: DiscNumberBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            itemView as TextView
            (itemView as TextView).text = viewBinder.label
        }
    }
}
