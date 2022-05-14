package com.simplecityapps.shuttle.ui.screens.changelog

import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class NotesBinder(val note: String) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_notes, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Notes
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<NotesBinder>(itemView) {
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        override fun bind(viewBinder: NotesBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            subtitle.text = Html.fromHtml(viewBinder.note)
            subtitle.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
