package com.simplecityapps.shuttle.ui.screens.settings

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SettingsViewBinder(
    val settingsItem: SettingsMenuItem,
    val isSelected: Boolean,
    val listener: Listener
) : ViewBinder {

    interface Listener {
        fun onMenuItemClicked(settingsItem: SettingsMenuItem)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateView(R.layout.list_item_settings))
    }

    override fun viewType(): Int {
        return ViewTypes.Settings
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SettingsViewBinder

        if (settingsItem != other.settingsItem) return false

        return true
    }

    override fun hashCode(): Int {
        return settingsItem.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return isSelected == (other as? SettingsViewBinder)?.isSelected
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SettingsViewBinder>(itemView) {

        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val label: TextView = itemView.findViewById(R.id.label)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onMenuItemClicked(viewBinder!!.settingsItem)
            }
        }

        override fun bind(viewBinder: SettingsViewBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            icon.setImageResource(viewBinder.settingsItem.icon)
            label.text = viewBinder.settingsItem.title

            itemView.isActivated = viewBinder.isSelected
        }
    }
}