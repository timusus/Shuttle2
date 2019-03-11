package com.simplecityapps.shuttle.ui.screens.debug

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.debug.LogMessage
import java.text.SimpleDateFormat

class LogMessageBinder(val logMessage: LogMessage) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_debug_log, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.Log
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogMessageBinder

        if (logMessage != other.logMessage) return false

        return true
    }

    override fun hashCode(): Int {
        return logMessage.hashCode()
    }

    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<LogMessageBinder>(itemView) {

        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        private val tagTextView: TextView = itemView.findViewById(R.id.tagTextView)
        private val messageTextViewBinder: TextView = itemView.findViewById(R.id.messageTextView)

        override fun bind(viewBinder: LogMessageBinder) {
            super.bind(viewBinder)

            timestampTextView.text = dateFormat.format(viewBinder.logMessage.date)
            tagTextView.text = viewBinder.logMessage.priority.getPriorityString() + "/" + (viewBinder.logMessage.tag ?: "")
            messageTextViewBinder.text = viewBinder.logMessage.message
        }

        companion object {
            @SuppressLint("SimpleDateFormat")
            val dateFormat = SimpleDateFormat("hh:mm:ss.SSS")
        }

        private fun Int.getPriorityString(): String {
            return when (this) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> ""
            }
        }
    }
}