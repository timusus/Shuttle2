package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class MediaProviderBinder(
    val providerType: MediaProvider.Type,
    val listener: Listener?,
    val showRemoveButton: Boolean,
    val showSubtitle: Boolean
) : ViewBinder {

    interface Listener {
        fun onItemClicked(providerType: MediaProvider.Type) {}
        fun onOverflowClicked(view: View, providerType: MediaProvider.Type) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_media_provider, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.MediaProvider
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaProviderBinder

        if (providerType != other.providerType) return false

        return true
    }

    override fun hashCode(): Int {
        return providerType.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<MediaProviderBinder>(itemView) {

        val icon: ImageView = itemView.findViewById(R.id.icon)
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onItemClicked(viewBinder!!.providerType)
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(overflowButton, viewBinder!!.providerType)
            }
        }

        override fun bind(viewBinder: MediaProviderBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.providerType.title()
            subtitle.text = viewBinder.providerType.description()
            icon.setImageDrawable(AppCompatResources.getDrawable(itemView.context, viewBinder.providerType.iconResId()))

            overflowButton.isVisible = viewBinder.showRemoveButton
            subtitle.isVisible = viewBinder.showSubtitle
        }
    }
}