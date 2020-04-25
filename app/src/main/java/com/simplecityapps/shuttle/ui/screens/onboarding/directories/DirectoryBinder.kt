package com.simplecityapps.shuttle.ui.screens.onboarding.directories

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class DirectoryBinder(
    val data: MusicDirectoriesContract.View.Data,
    val listener: Listener
) : ViewBinder {

    interface Listener {
        fun onRemoveClicked(data: MusicDirectoriesContract.View.Data)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateView(R.layout.list_item_onboaring_directory))
    }

    override fun viewType(): Int {
        return ViewTypes.OnboardingDirectory
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<DirectoryBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.titleLabel)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitleLabel)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)

        init {
            removeButton.setOnClickListener { viewBinder?.listener?.onRemoveClicked(viewBinder!!.data) }
        }

        override fun bind(viewBinder: DirectoryBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.data.tree.documentId
            progressBar.isVisible = !viewBinder.data.traversalComplete
            if (viewBinder.data.traversalComplete) {
                val leaves = viewBinder.data.tree.getLeaves()
                subtitle.text = "${leaves.size} audio file${if (leaves.size == 1) "" else "s"}"
                progressBar.isVisible = false
            } else {
                progressBar.isVisible = true
                subtitle.text = "Scanning..."
            }
        }
    }
}