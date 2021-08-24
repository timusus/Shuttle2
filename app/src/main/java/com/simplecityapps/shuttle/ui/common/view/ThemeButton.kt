package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R

class ThemeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

    private val image: ImageView
    private val label: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.button_theme, this, true)

        image = findViewById(R.id.image)
        label = findViewById(R.id.label)

        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        orientation = VERTICAL

        context.theme.obtainStyledAttributes(attrs, R.styleable.ThemeButton, 0, 0).use { typedArray ->
            label.text = typedArray.getString(R.styleable.ThemeButton_label)
            image.setImageResource(typedArray.getResourceId(R.styleable.ThemeButton_icon, 0))
        }

        setBackgroundResource(R.drawable.ripple)
    }

    override fun setActivated(activated: Boolean) {
        super.setActivated(activated)

        label.typeface = if (activated) ResourcesCompat.getFont(context, R.font.opensans_semibold) else ResourcesCompat.getFont(context, R.font.opensans_regular)
    }
}
