package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R
import kotlinx.android.synthetic.main.button_seek.view.*

class SeekButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var seekSeconds: Int = 15
        set(value) {
            field = value
            textView.text = value.toString()
        }

    private var forward: Boolean = true
        set(value) {
            field = value
            textView.setBackgroundResource(if (forward) R.drawable.ic_seek_forward_black_24dp else R.drawable.ic_seek_backward_black_24dp)
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.button_seek, this, true)

        isClickable = true
        isFocusable = true

        TypedValue().apply {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, this, true)
            setBackgroundResource(resourceId)
        }

        context.theme.obtainStyledAttributes(attrs, R.styleable.SeekButton, 0, 0).use { typedArray ->
            this.forward = typedArray.getBoolean(R.styleable.SeekButton_forward, true)
            this.seekSeconds = typedArray.getInteger(R.styleable.SeekButton_seconds, 15)
        }
    }
}