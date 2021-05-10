package com.simplecityapps.shuttle.ui.screens.sleeptimer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class SleepTimerBinder(val duration: SleepTimerDuration, val listener: Listener) : ViewBinder {

    interface Listener {
        fun onClick(duration: SleepTimerDuration)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_sleep_timer, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.SleepTimer
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<SleepTimerBinder>(itemView) {

        val label: TextView = itemView.findViewById(R.id.label)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onClick(viewBinder!!.duration)
            }
        }

        override fun bind(viewBinder: SleepTimerBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            label.setText(viewBinder.duration.nameResId)
        }
    }
}