package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageButton
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.simplecityapps.shuttle.R

class FavoriteButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr), Checkable {
    private var heartDrawable = AnimatedVectorDrawableCompat.create(context, R.drawable.avd_heart)!!
    private var heartDrawableReverse = AnimatedVectorDrawableCompat.create(context, R.drawable.avd_heart_reverse)!!

    private var isChecked = false

    init {
        if (isChecked) {
            setImageDrawable(heartDrawableReverse)
        } else {
            setImageDrawable(heartDrawable)
        }
    }

    override fun isChecked(): Boolean {
        return isChecked
    }

    override fun setChecked(isChecked: Boolean) {
        (drawable as AnimatedVectorDrawableCompat).stop()

        if (this.isChecked != isChecked) {
            this.isChecked = isChecked

            if (isChecked) {
                setImageDrawable(heartDrawable)
            } else {
                setImageDrawable(heartDrawableReverse)
            }

            (drawable as AnimatedVectorDrawableCompat).start()
        }
    }

    override fun toggle() {
        setChecked(!isChecked)
    }
}
