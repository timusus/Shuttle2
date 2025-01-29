package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class ShuffleBinder(
    @StringRes val titleIdRes: Int,
    val listener: Listener
) : ViewBinder {
    interface Listener {
        fun onClicked()
    }

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_shuffle, parent, false))

    override fun viewType(): Int = ViewTypes.AlbumShuffle

    override fun spanSize(spanCount: Int): Int = spanCount

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ShuffleBinder>(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.title)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onClicked()
            }
        }

        override fun bind(
            viewBinder: ShuffleBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            titleText.setText(viewBinder.titleIdRes)
        }
    }
}
