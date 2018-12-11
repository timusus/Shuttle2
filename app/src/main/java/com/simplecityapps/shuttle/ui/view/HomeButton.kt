package com.simplecityapps.shuttle.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R
import kotlinx.android.synthetic.main.button_home.view.*

class HomeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        LayoutInflater.from(context).inflate(R.layout.button_home, this, true)
    }

    fun setType(type: Type) {
        label.setText(type.text())
        image.setImageResource(type.image())
        image.setBackgroundResource(R.drawable.circle_filled)
        image.backgroundTintList = ColorStateList.valueOf(image.resources.getColor(type.backgroundColor()))
        image.imageTintList = ColorStateList.valueOf(image.resources.getColor(type.foregroundColor()))
    }

    enum class Type {
        History, Latest, Favorites, Shuffle;

        @DrawableRes
        fun image(): Int {
            return when (this) {
                History -> R.drawable.ic_history_black_24dp
                Latest -> R.drawable.ic_queue_black_24dp
                Favorites -> R.drawable.ic_favorite_border_black_24dp
                Shuffle -> R.drawable.ic_shuffle_black_24dp
            }
        }

        @ColorRes
        fun backgroundColor(): Int {
            return when (this) {
                History -> R.color.history_green_light
                Latest -> R.color.latest_yellow_light
                Favorites -> R.color.favorite_red_light
                Shuffle -> R.color.shuffle_blue_light
            }
        }

        @ColorRes
        fun foregroundColor(): Int {
            return when (this) {
                History -> R.color.history_green_dark
                Latest -> R.color.latest_yellow_dark
                Favorites -> R.color.favorite_red_dark
                Shuffle -> R.color.shuffle_blue_dark
            }
        }

        @StringRes
        fun text(): Int {
            return when (this) {
                History -> R.string.btn_history
                Latest -> R.string.btn_latest
                Favorites -> R.string.btn_favorite
                Shuffle -> R.string.btn_shuffle
            }
        }
    }

}