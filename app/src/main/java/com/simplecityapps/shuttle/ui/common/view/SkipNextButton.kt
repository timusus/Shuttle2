package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.simplecityapps.shuttle.R

class SkipNextButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        setImageResource(R.drawable.ic_skip_next_black_24dp)
    }
}