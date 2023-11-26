package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R

class HomeButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val image: ImageView
    private val label: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.button_home, this, true)

        image = findViewById(R.id.image)
        label = findViewById(R.id.label)

        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        orientation = VERTICAL

        TypedValue().apply {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
            setBackgroundResource(resourceId)
        }

        context.theme.obtainStyledAttributes(attrs, R.styleable.HomeButton, 0, 0).use { typedArray ->
            ButtonType.values().firstOrNull { buttonType -> buttonType.value == typedArray.getInt(R.styleable.HomeButton_type, -1) }?.let { buttonType ->
                setType(buttonType)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setType(type: ButtonType) {
        label.setText(type.data.text)
        image.setImageResource(type.data.image)
        image.setBackgroundResource(R.drawable.circle_filled)
        image.backgroundTintList = ColorStateList.valueOf(getColor(type.data.backgroundColor))
        image.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, type.data.foregroundColor))
    }

    private fun getColor(
        @AttrRes res: Int
    ): Int {
        with(TypedValue()) {
            context.theme.resolveAttribute(res, this, true)
            return ContextCompat.getColor(context, resourceId)
        }
    }

    enum class ButtonType(val value: Int) {
        History(0),
        Recent(1),
        Favorites(2),
        Shuffle(3)
        ;

        data class Data(
            @DrawableRes val image: Int,
            @AttrRes val backgroundColor: Int,
            @ColorRes val foregroundColor: Int,
            @StringRes val text: Int
        )

        val data: Data
            get() {
                return when (this) {
                    History -> history
                    Recent -> recent
                    Favorites -> favorites
                    Shuffle -> shuffle
                }
            }

        companion object {
            val history = Data(R.drawable.ic_history_black_24dp, R.attr.historyButtonBackgroundColor, R.color.history_green_tint, R.string.btn_history)
            val recent = Data(R.drawable.ic_queue_black_24dp, R.attr.recentlyAddedButtonBackgroundColor, R.color.recently_added_yellow_tint, R.string.btn_recently_added)
            val favorites = Data(R.drawable.ic_favorite_border_black_24dp, R.attr.favoritesButtonBackgroundColor, R.color.favorite_red_tint, R.string.btn_favorite)
            val shuffle = Data(R.drawable.ic_shuffle_black_24dp, R.attr.shuffleButtonBackgroundColor, R.color.shuffle_blue_tint, R.string.btn_shuffle)
        }
    }
}
