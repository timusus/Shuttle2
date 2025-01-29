package au.com.simplecityapps.shuttle.imageloading.palette

import android.animation.ArgbEvaluator
import android.animation.TypeEvaluator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.WorkerThread

class ColorSet(
    var primaryColor: Int,
    var accentColor: Int,
    var primaryTextColorTinted: Int,
    var secondaryTextColorTinted: Int,
    var primaryTextColor: Int,
    var secondaryTextColor: Int
) {
    companion object {
        private val bitmapPaletteProcessor = BitmapPaletteProcessor()
        private val colorHelper = ColorHelper()

        @WorkerThread
        fun fromBitmap(
            context: Context,
            bitmap: Bitmap
        ): ColorSet {
            val colors = bitmapPaletteProcessor.processBitmap(bitmap)
            val tintedTextColors = colorHelper.ensureColors(context, true, colors.first!!, colors.second!!)

            val primaryTextColor = ColorHelper.resolvePrimaryColor(context, colors.first!!)
            val secondaryTextColor = ColorHelper.resolveSecondaryColor(context, colors.first!!)

            return ColorSet(colors.first!!, colors.second!!, tintedTextColors.first!!, tintedTextColors.second!!, primaryTextColor, secondaryTextColor)
        }

        fun fromPrimaryAccentColors(
            context: Context,
            primaryColor: Int,
            accentColor: Int
        ): ColorSet {
            val tintedTextColor = colorHelper.ensureColors(context, true, primaryColor, accentColor)

            val primaryTextColor = ColorHelper.resolvePrimaryColor(context, primaryColor)
            val secondaryTextColor = ColorHelper.resolveSecondaryColor(context, primaryColor)

            return ColorSet(primaryColor, accentColor, tintedTextColor.first!!, tintedTextColor.second!!, primaryTextColor, secondaryTextColor)
        }

        fun empty(): ColorSet = ColorSet(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT)

        /**
         * @return an approximate byte size for this object. Currently based on 6 integers @ 4 bytes each and a safety factor of 3
         */
        fun estimatedSize(): Int = 6 * 4 * 3
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColorSet

        if (primaryColor != other.primaryColor) return false
        if (accentColor != other.accentColor) return false
        if (primaryTextColorTinted != other.primaryTextColorTinted) return false
        if (secondaryTextColorTinted != other.secondaryTextColorTinted) return false
        if (primaryTextColor != other.primaryTextColor) return false
        if (secondaryTextColor != other.secondaryTextColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primaryColor
        result = 31 * result + accentColor
        result = 31 * result + primaryTextColorTinted
        result = 31 * result + secondaryTextColorTinted
        result = 31 * result + primaryTextColor
        result = 31 * result + secondaryTextColor
        return result
    }
}

class ColorSetEvaluator : TypeEvaluator<ColorSet> {
    private val argbEvaluator: ArgbEvaluator = ArgbEvaluator()

    override fun evaluate(
        p0: Float,
        p1: ColorSet,
        p2: ColorSet
    ): ColorSet = ColorSet(
        argbEvaluator.evaluate(p0, p1.primaryColor, p2.primaryColor) as Int,
        argbEvaluator.evaluate(p0, p1.accentColor, p2.accentColor) as Int,
        argbEvaluator.evaluate(p0, p1.primaryTextColorTinted, p2.primaryTextColor) as Int,
        argbEvaluator.evaluate(p0, p1.secondaryTextColorTinted, p2.secondaryTextColorTinted) as Int,
        argbEvaluator.evaluate(p0, p1.primaryTextColor, p2.primaryTextColor) as Int,
        argbEvaluator.evaluate(p0, p1.secondaryTextColor, p2.secondaryTextColor) as Int
    )
}
