package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R
import java.util.concurrent.TimeUnit

class SkipButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    interface OnSeekListener {
        fun onSeek(seekAmount: Int)
    }

    val repeatInterval = TimeUnit.MILLISECONDS.toMillis(500)

    var listener: OnSeekListener? = null

    private var skipDrawable: Drawable? = null

    private var seekAmount: Int = 15

    private var direction: SeekDirection = SeekDirection.Forward
        set(value) {
            field = value

            skipDrawable = ContextCompat.getDrawable(
                context,
                when (direction) {
                    SeekDirection.Forward -> R.drawable.ic_skip_next_black_24dp
                    SeekDirection.Backward -> R.drawable.ic_skip_previous_black_24dp
                }
            )!!.mutate()
        }

    init {
        isLongClickable = true
        isFocusable = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.SkipButton, 0, 0).use { typedArray ->
            this.direction = SeekDirection.fromInt(typedArray.getInt(R.styleable.SkipButton_seekDirection, 0))
            this.seekAmount = typedArray.getInteger(R.styleable.SkipButton_seekAmount, 15)
        }

        TypedValue().apply {
            context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, this, true)
            setBackgroundResource(resourceId)
        }

        TypedValue().apply {
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, this, true)
            val color = ContextCompat.getColor(context, resourceId)
            skipDrawable?.setTint(color)
        }

        setImageDrawable(skipDrawable)
    }

    override fun performLongClick(): Boolean {
        post(runnable)
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            removeCallbacks(runnable)
        }
        return super.onTouchEvent(event)
    }

    private val runnable = object : Runnable {
        override fun run() {
            listener?.onSeek(seekAmount)
            if (isPressed) {
                postDelayed(this, repeatInterval)
            }
        }
    }
}