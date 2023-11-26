package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.use
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.sp
import kotlin.math.max

class BadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var badgeCount: Int = 12
        set(value) {
            field = value
            invalidate()
            requestLayout()
        }

    private val badgeCountString: String
        get() {
            if (badgeCount >= 100) {
                return "99+"
            }
            return badgeCount.toString()
        }

    private val paint = Paint()
    private val textPaint = TextPaint()

    private val bounds = RectF()

    init {
        paint.setShadowLayer(2f, 0f, 0f, 0xCC000000.toInt())
        paint.isAntiAlias = true

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true

        setPadding(5.dp, 3.dp, 5.dp, 3.dp)

        context.theme.obtainStyledAttributes(attrs, R.styleable.BadgeView, 0, 0).use { typedArray ->
            textPaint.color = typedArray.getColor(R.styleable.BadgeView_android_textColor, Color.WHITE)
            paint.color = typedArray.getColor(R.styleable.BadgeView_android_background, Color.BLACK)
            textPaint.textSize = typedArray.getDimension(R.styleable.BadgeView_android_textSize, 12f.sp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                paint.typeface = typedArray.getFont(R.styleable.BadgeView_android_fontFamily)
            } else {
                ResourcesCompat.getFont(context, typedArray.getResourceId(R.styleable.BadgeView_android_fontFamily, -1))?.let { typeface ->
                    paint.typeface = typeface
                }
            }
        }

        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (textPaint.descent() - textPaint.ascent()) + paddingTop + paddingBottom
        val desiredWidth = max(desiredHeight, (textPaint.measureText(badgeCountString) + paddingLeft + paddingRight))

        setMeasuredDimension(
            desiredWidth.toInt(),
            desiredHeight.toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bounds.set(1f, 1f, width.toFloat() - 1f, height.toFloat() - 1f)

        canvas.drawRoundRect(bounds, height / 2f, height / 2f, paint)

        val textHeight = textPaint.descent() - textPaint.ascent()
        val textOffset = textHeight / 2 - textPaint.descent()
        canvas.drawText(badgeCountString, bounds.centerX(), bounds.centerY() + textOffset, textPaint)
    }

    fun setCircleBackgroundColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }
}
