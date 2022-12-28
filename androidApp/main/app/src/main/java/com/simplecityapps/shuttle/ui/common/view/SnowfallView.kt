package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import com.google.android.material.math.MathUtils.lerp
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SnowfallView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    fun setForecast(forecast: Double) {
        if (random.nextDouble() <= forecast) {
            letItSnow()
        }
    }

    /** Used to paint each snowflake  */
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    /** The snowflakes currently falling  */
    private val snowflakes: MutableList<Snowflake> = mutableListOf()

    /** Used to randomly generate snowflake params  */
    private val random: Random = SecureRandom()

    /** Used to delay snowfall  */
    private val snowHandler = Handler()

    /** Wait a few seconds before displaying snow  */
    private val snowfallDelay
        get() = TimeUnit.SECONDS.toMillis(
            random.nextInt(60)
                .coerceAtLeast(5).toLong()
        )

    /** The duration for which it will snow**/
    private val snowfallDuration
        get() = TimeUnit.SECONDS.toMillis(
            random.nextInt(180)
                .coerceAtLeast(60).toLong()
        )

    /** Interval between adding more snow  */
    private val snowfallTimeIncrement
        get() = TimeUnit.SECONDS.toMillis(
            random.nextInt(8)
                .coerceAtLeast(2).toLong()
        )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (snowflakes.isNotEmpty()) {
            for (i in snowflakes.indices.reversed()) {
                val snowflake = snowflakes[i]
                if (snowflake.snowY().roundToInt() > height) {
                    if (snowflake.shouldRemove) {
                        snowflakes.remove(snowflake)
                    } else {
                        snowflake.reset()
                    }
                }
                snowPaint.alpha = snowflake.alpha
                canvas.drawCircle(snowflake.snowX(), snowflake.snowY(), snowflake.snowR, snowPaint)
            }
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()


    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    private fun letItSnow() {
        if (!isSnowing) {
            snowHandler.removeCallbacksAndMessages(null)
            snowHandler.postDelayed({ generateSnow() }, snowfallDelay)
            snowHandler.postDelayed({ removeSnow() }, snowfallDuration)
        }
    }

    private val isSnowing: Boolean
        get() = snowflakes.isNotEmpty()

    fun clear() {
        snowHandler.removeCallbacksAndMessages(null)
        snowflakes.clear()
        invalidate()
    }

    private fun generateSnow() {
        if (snowflakes.size <= TOTAL_FLAKES) {
            val flakesToAdd = (10 + random.nextInt(FLAKE_INCREMENT)).coerceAtMost(TOTAL_FLAKES - snowflakes.size)
            if (flakesToAdd > 0) {
                addSnow(flakesToAdd)
                snowHandler.postDelayed({ generateSnow() }, snowfallTimeIncrement)
            }
        }
    }

    private fun addSnow(numFlakes: Int) {
        for (i in 0 until numFlakes) {
            val angle = Math.toRadians(lerp(MIN_ANGLE, MAX_ANGLE, random.nextFloat()).toDouble())
            val speed: Float = lerp(MIN_SPEED, MAX_SPEED, random.nextFloat())
            val velX = (speed.toDouble() * StrictMath.cos(angle)).toFloat()
            val velY = (speed.toDouble() * StrictMath.sin(angle)).toFloat()
            val size: Float = lerp(MIN_SIZE, MAX_SIZE, random.nextFloat())
            val startX: Float = lerp(0f, width.toFloat(), random.nextFloat())
            var startY: Float = lerp(0f, height.toFloat(), random.nextFloat())
            startY -= height.toFloat() - size
            val alpha = lerp(MIN_ALPHA.toFloat(), MAX_ALPHA.toFloat(), random.nextFloat()).toInt()
            snowflakes.add(Snowflake(startX, startY, velX, velY, size, alpha))
        }
        invalidate()
    }

    private fun removeSnow() {
        if (snowflakes.isNotEmpty()) {
            snowHandler.removeCallbacksAndMessages(null)
            for (snowflake in snowflakes) {
                snowflake.shouldRemove = true
            }
        }
    }

    companion object {

        /** The total number of snowflakes to generate  */
        private const val TOTAL_FLAKES = 200

        /** The increment with which to generate more snowflakes  */
        private const val FLAKE_INCREMENT = 30

        /** Default min and max snowflake alpha  */
        private const val MIN_ALPHA = 100
        private const val MAX_ALPHA = 250

        /** Default min and max snowflake angle  */
        private const val MIN_ANGLE = 80f
        private const val MAX_ANGLE = 100f

        /** Default min and max snowflake velocity  */
        private const val MIN_SPEED = 1f
        private const val MAX_SPEED = 5f

        /** Default min and max snowflake size  */
        private const val MIN_SIZE = 2f
        private const val MAX_SIZE = 15f
    }
}

class Snowflake(
    private val startX: Float,
    private val startY: Float,
    private val velX: Float,
    private val velY: Float,
    val snowR: Float,
    val alpha: Int
) {
    var snowX: Float = startX
    var snowY: Float = startY
    var shouldRemove = false

    fun snowX(): Float {
        return velX.let { snowX += it; snowX }
    }

    fun snowY(): Float {
        return velY.let { snowY += it; snowY }
    }

    fun reset() {
        snowX = startX
        snowY = startY
    }
}
