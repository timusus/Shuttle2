package com.simplecityapps.shuttle.ui.screens.home.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class HeaderBinder(
    val title: String,
    val subtitle: String? = null
) : ViewBinder {
    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_header, parent, false))

    override fun viewType(): Int = ViewTypes.Header

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HeaderBinder

        if (title != other.title) return false
        if (subtitle != other.subtitle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (subtitle?.hashCode() ?: 0)
        return result
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<HeaderBinder>(itemView) {
        private val titleLabel: TextView = itemView.findViewById(R.id.titleLabel)
        private val subTitleLabel: TextView = itemView.findViewById(R.id.subtitleLabel)

        override fun bind(
            viewBinder: HeaderBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            titleLabel.text = viewBinder.title

            subTitleLabel.text = viewBinder.subtitle
            subTitleLabel.isVisible = viewBinder.subtitle != null
        }
    }
}
