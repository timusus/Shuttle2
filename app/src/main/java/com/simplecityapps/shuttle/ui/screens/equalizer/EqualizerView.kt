package com.simplecityapps.shuttle.ui.screens.equalizer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.math.MathUtils.lerp
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerBand
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.view.VerticalSeekbar

class EqualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onBandChanged(band: EqualizerBand)
    }

    var listener: Listener? = null

    var equalizer: Equalizer.Presets.Preset = Equalizer.Presets.custom
        set(value) {
            field = value
            updateBands()
        }

    private var maxGain: Int = 12

    private var seekbarViews = mutableListOf<VerticalSeekbar>()

    private var animator: ValueAnimator? = null

    init {
        orientation = HORIZONTAL
        equalizer = Equalizer.Presets.custom
        layoutBands()
    }

    override fun setActivated(activated: Boolean) {
        super.setActivated(activated)

        seekbarViews.forEach { it.isActivated = activated }
    }

    fun configure(maxGain: Int, equalizer: Equalizer.Presets.Preset) {
        this.maxGain = maxGain
        this.equalizer = equalizer
    }

    @SuppressLint("SetTextI18n")
    private fun layoutBands() {
        removeAllViews()

        equalizer.bands.forEachIndexed { index, band ->
            val bandView = View.inflate(context, R.layout.equalizer_band, null)

            if (index == 0) {
                bandView.findViewById<View>(R.id.maxGainLabel).isVisible = true
                bandView.findViewById<View>(R.id.zeroGainLabel).isVisible = true
                bandView.findViewById<View>(R.id.minGainLabel).isVisible = true
            }

            val seekbar: VerticalSeekbar = bandView.findViewById(R.id.seekbar)
            seekbar.listener = object : VerticalSeekbar.Listener {
                override fun onStopTracking(progress: Float) {
                    val gain = (maxGain - ((maxGain * 2f) * progress)).toInt()
                    listener?.onBandChanged(EqualizerBand(band.centerFrequency, gain))
                }

                override fun onProgressChanged(progress: Float) {
                    val gain = (maxGain - ((maxGain * 2f) * progress)).toInt()
                    listener?.onBandChanged(EqualizerBand(band.centerFrequency, gain))
                }
            }
            seekbar.progress = (maxGain - band.gain) / (maxGain * 2f)

            val textView: TextView = bandView.findViewById(R.id.bandLabel)

            if (band.centerFrequency >= 1000) {
                textView.text = "${band.centerFrequency / 1000}kHz"
            } else {
                textView.text = "${band.centerFrequency}Hz"
            }

            seekbar.isActivated = isActivated

            seekbarViews.add(seekbar)

            addView(bandView)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        animator?.cancel()
    }

    private fun updateBands() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                equalizer.bands.forEachIndexed { index, band ->
                    seekbarViews.getOrNull(index)?.apply {
                        progress = lerp(progress, ((maxGain - band.gain) / (maxGain * 2f)), animatedValue as Float)
                        invalidate()
                    }
                }
            }
            start()
        }
    }
}