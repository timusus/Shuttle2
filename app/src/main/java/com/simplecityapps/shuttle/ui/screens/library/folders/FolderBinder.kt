package com.simplecityapps.shuttle.ui.screens.library.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R

class FolderBinder(val node: Node<Song>) : ViewBinder {

    interface Listener {
        fun onNodeSelected(node: Node<Song>)
    }

    var listener: Listener? = null

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_folder, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.Folder
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FolderBinder

        if (node != other.node) return false

        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<FolderBinder>(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.title)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onNodeSelected(viewBinder!!.node)
            }
        }

        override fun bind(viewBinder: FolderBinder) {
            super.bind(viewBinder)

            title.text = viewBinder.node.name
        }
    }
}