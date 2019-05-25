package com.simplecityapps.shuttle.ui.screens.library.folders

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class FolderBinder(
    val treeNode: Tree<Node<Song>>
) : ViewBinder,
SectionViewBinder{

    interface Listener {
        fun onNodeSelected(node: Node<Song>)
    }

    var listener: Listener? = null

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_folder, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Folder
    }

    override fun getSectionName(): String? {
        return treeNode.node.name.firstOrNull()?.toString() ?: ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FolderBinder

        if (treeNode != other.treeNode) return false

        return true
    }

    override fun hashCode(): Int {
        return treeNode.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<FolderBinder>(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.title)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onNodeSelected(viewBinder!!.treeNode.node)
            }
        }

        override fun bind(viewBinder: FolderBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.treeNode.node.name

            if (viewBinder.treeNode.node.data == null) {
                imageView.setImageResource(R.drawable.ic_folder_open_black_24dp)
                imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.primary_material_dark))
            } else {
                imageView.setImageResource(R.drawable.ic_audiotrack_black_24dp)
                imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.colorPrimary))
            }
        }
    }
}