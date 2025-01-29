package com.simplecityapps.shuttle.ui.screens.settings.screens.appearance

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class LibraryTabBinder(val tab: LibraryTab, val selected: Boolean, val listener: Listener) : ViewBinder {
    interface Listener {
        fun onStartDrag(viewHolder: ViewHolder)

        fun onChecked(
            tab: LibraryTab,
            isChecked: Boolean
        )
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_library_tab, parent, false))

    override fun viewType(): Int = ViewTypes.LibraryTab

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LibraryTabBinder

        if (tab != other.tab) return false

        return true
    }

    override fun hashCode(): Int = tab.hashCode()

    override fun areContentsTheSame(other: Any): Boolean = (other as? LibraryTabBinder)?.selected == selected

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<LibraryTabBinder>(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)

        init {
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    viewBinder?.listener?.onStartDrag(this)
                }
                true
            }

            checkbox.setOnCheckedChangeListener { _, b ->
                viewBinder?.listener?.onChecked(viewBinder!!.tab, b)
            }
        }

        override fun bind(
            viewBinder: LibraryTabBinder,
            isPartial: Boolean
        ) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.tab.name(itemView.resources)
            checkbox.isChecked = viewBinder.selected
        }
    }
}

private fun LibraryTab.name(resources: Resources): String = when (this) {
    LibraryTab.Genres -> resources.getString(R.string.genres)
    LibraryTab.Playlists -> resources.getString(R.string.library_playlists)
    LibraryTab.Artists -> resources.getString(R.string.artists)
    LibraryTab.Albums -> resources.getString(R.string.albums)
    LibraryTab.Songs -> resources.getString(R.string.songs)
}
