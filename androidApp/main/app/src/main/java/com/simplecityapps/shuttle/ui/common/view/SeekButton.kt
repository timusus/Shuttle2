package com.simplecityapps.shuttle.ui.common.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.simplecityapps.shuttle.R

class SeekButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSeekListener {

        /**
         * The seek amount, in seconds
         */
        fun onSeek(seekAmount: Int)
    }

    private var textColor: Int = Color.BLACK
    private val textPaint = Paint()
    private val textRect = Rect()

    private var seekDrawable: Drawable? = null

    private var seekAmount: Int = 15

    private var direction: SeekDirection = SeekDirection.Forward
        set(value) {
            field = value

            seekDrawable = ContextCompat.getDrawable(
                context,
                when (direction) {
                    SeekDirection.Forward -> R.drawable.ic_seek_forward_black_24dp
                    SeekDirection.Backward -> R.drawable.ic_seek_backward_black_24dp
                }
            )!!.mutate()
        }

    var listener: OnSeekListener? = null

    private var seekRotation = 0f
    private var animator: ValueAnimator? = null

    init {

        isClickable = true
        isFocusable = true

        context.theme.obtainStyledAttributes(attrs, R.styleable.SeekButton, 0, 0).use { typedArray ->
            this.direction = SeekDirection.fromInt(typedArray.getInt(R.styleable.SeekButton_seekDirection, 0))
            this.seekAmount = typedArray.getInteger(R.styleable.SeekButton_seekAmount, 15)
        }

        TypedValue().apply {
            context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, this, true)
            setBackgroundResource(resourceId)
        }

        TypedValue().apply {
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, this, true)
            val color = ContextCompat.getColor(context, resourceId)
            textColor = color
            seekDrawable?.setTint(color)
        }

        if (!isInEditMode) {
            textPaint.typeface = ResourcesCompat.getFont(context, R.font.opensans_semibold)
        }
        textPaint.color = textColor
        textPaint.textSize = resources.displayMetrics.scaledDensity * 10
        textPaint.textAlign = Paint.Align.LEFT

        setWillNotDraw(false)

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 360f)
        animator!!.duration = 450
        animator!!.interpolator = FastOutSlowInInterpolator()
        animator!!.addUpdateListener { animator ->
            seekRotation = (animator.animatedValue as Float) * (if (direction == SeekDirection.Forward) 1f else -1f)
            invalidate()
        }
        animator!!.addListener(onEnd = { rotation = 0f })

        setOnClickListener {
            listener?.onSeek(seekAmount)

            animator?.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val text = seekAmount.toString()
        canvas.getClipBounds(textRect)
        val textHeight = textRect.height()
        val textWidth = textRect.width()
        textPaint.getTextBounds(text, 0, text.length, textRect)
        canvas.drawText(
            text,
            textWidth / 2f - textRect.width() / 2f - textRect.left,
            (textHeight / 2f + textRect.height() / 2f - textRect.bottom),
            textPaint
        )

        canvas.rotate(seekRotation, width / 2f, height / 2f)
        seekDrawable?.let { seekDrawable ->
            seekDrawable.setBounds(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
            seekDrawable.draw(canvas)
        }
    }
}