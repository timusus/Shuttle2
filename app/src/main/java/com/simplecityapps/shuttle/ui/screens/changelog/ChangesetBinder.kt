package com.simplecityapps.shuttle.ui.screens.changelog

import android.graphics.Typeface.BOLD
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp
import java.text.DateFormat

class ChangesetBinder(
    val expanded: Boolean = false,
    val changeset: Changeset,
    val listener: Listener?
) : ViewBinder {

    interface Listener {
        fun onItemClicked(position: Int, expanded: Boolean)
    }

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_changeset, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Changelog
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChangesetBinder

        if (changeset != other.changeset) return false

        return true
    }

    override fun hashCode(): Int {
        return changeset.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return (other as? ChangesetBinder)?.expanded == expanded
    }

    fun getSpannableString(changeset: Changeset): Spannable {
        val spannableBuilder = SpannableStringBuilder()

        if (changeset.features.isNotEmpty()) {
            spannableBuilder.append(
                SpannableString("Features").apply {
                    setSpan(
                        StyleSpan(BOLD),
                        0,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                })
            spannableBuilder.append("\n\n")
            changeset.features.forEachIndexed { index, string ->
                spannableBuilder.append(
                    SpannableString(string).apply {
                        setSpan(
                            BulletSpan(8.dp),
                            0,
                            length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                )
                if (index != changeset.features.size - 1) {
                    spannableBuilder.append("\n\n")
                }
            }

            if (changeset.improvements.isNotEmpty() || changeset.fixes.isNotEmpty()) {
                spannableBuilder.append("\n\n\n")
            }
        }

        if (changeset.improvements.isNotEmpty()) {
            spannableBuilder.append(
                SpannableString("Improvements").apply {
                    setSpan(
                        StyleSpan(BOLD),
                        0,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                })
            spannableBuilder.append("\n\n")
            changeset.improvements.forEachIndexed { index, string ->
                spannableBuilder.append(
                    SpannableString(string).apply {
                        setSpan(
                            BulletSpan(8.dp),
                            0,
                            length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                )
                if (index != changeset.improvements.size - 1) {
                    spannableBuilder.append("\n\n")
                }
            }
            if (changeset.fixes.isNotEmpty()) {
                spannableBuilder.append("\n\n\n")
            }
        }

        if (changeset.fixes.isNotEmpty()) {
            spannableBuilder.append(
                SpannableString("Bug Fixes").apply {
                    setSpan(
                        StyleSpan(BOLD),
                        0,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                })
            spannableBuilder.append("\n\n")
            changeset.fixes.forEachIndexed { index, string ->
                spannableBuilder.append(
                    SpannableString(string).apply {
                        setSpan(
                            BulletSpan(8.dp),
                            0,
                            length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                )
                if (index != changeset.fixes.size - 1) {
                    spannableBuilder.append("\n\n")
                }
            }
        }

        return spannableBuilder
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ChangesetBinder>(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        val textArea: TextView = itemView.findViewById(R.id.textArea)
        val expandedIcon: ImageView = itemView.findViewById(R.id.expandedIcon)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onItemClicked(adapterPosition, viewBinder!!.expanded)
            }
        }

        override fun bind(viewBinder: ChangesetBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.changeset.versionName
            subtitle.text = dateFormat.format(viewBinder.changeset.date)
            textArea.text = viewBinder.getSpannableString(viewBinder.changeset)
            textArea.isVisible = viewBinder.expanded
            expandedIcon.rotation = if (viewBinder.expanded) 180f else 0f
        }
    }

    companion object {
        val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    }
}

fun ChangesetBinder.clone(expanded: Boolean): ChangesetBinder {
    return ChangesetBinder(expanded, changeset, listener)
}