package com.simplecityapps.shuttle.ui.screens.equalizer

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.math.MathUtils
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paramsen.noise.Noise
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.fromDb
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FrequencyResponseDialogFragment : DialogFragment() {
    private var lineChart: LineChart by autoCleared()
    private var loadingView: CircularLoadingView by autoCleared()

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    private lateinit var preset: Equalizer.Presets.Preset

    private var textColor: Int = 0
    private var lineColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preset = playbackPreferenceManager.preset

        TypedValue().apply {
            requireContext().theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            textColor = ContextCompat.getColor(requireContext(), resourceId)
            requireContext().obtainStyledAttributes(data, intArrayOf(androidx.appcompat.R.attr.colorPrimary)).use { typedArray ->
                lineColor = typedArray.getColor(0, ContextCompat.getColor(requireContext(), R.color.colorPrimary))
            }
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(requireContext(), R.layout.fragment_frequency_response_dialog, null)

        lineChart = view.findViewById(R.id.lineChart)

        loadingView = view.findViewById(R.id.loadingView)
        loadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))

        lifecycleScope.launch {
            val fft = calculateFft()
            setData(fft)
            loadingView.setState(CircularLoadingView.State.None)
            lineChart.isVisible = true
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dsp_dialog_frequency_response_title))
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .show()
    }

    fun setData(data: Map<Float, Float>) {
        val entries = data.map { Entry(log10(it.key), it.value) }

        val dataset = LineDataSet(entries, getString(R.string.dsp_dialog_frequency_response_title))
        dataset.mode = LineDataSet.Mode.HORIZONTAL_BEZIER
        dataset.lineWidth = 1f
        dataset.setDrawCircles(false)
        dataset.setDrawHorizontalHighlightIndicator(false)
        dataset.setDrawVerticalHighlightIndicator(false)
        dataset.color = lineColor

        val chartData = LineData(dataset)
        chartData.setDrawValues(false)
        lineChart.data = chartData

        lineChart.axisLeft.textColor = textColor
        lineChart.axisLeft.axisMinimum = -20f
        lineChart.axisLeft.axisMaximum = 20f
        lineChart.axisLeft.labelCount = 10
        lineChart.axisLeft.valueFormatter =
            object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f".format(value) + "dB"
            }
        lineChart.axisRight.isEnabled = false

        lineChart.xAxis.textColor = textColor
        lineChart.xAxis.axisMinimum = 1f
        lineChart.xAxis.axisMaximum = log10(20500f)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.setDrawAxisLine(false)
        lineChart.xAxis.setLabelCount(8, true)
        lineChart.xAxis.valueFormatter =
            object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val unscaled = 10f.pow(value)
                    return if (unscaled >= 1000) {
                        "%.0f".format(unscaled / 1000) + " kHz"
                    } else {
                        "%.0f".format(unscaled) + " Hz"
                    }
                }
            }

        lineChart.description.isEnabled = false
        lineChart.setScaleEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.legend.isEnabled = false
        lineChart.setNoDataText("")

        lineChart.notifyDataSetChanged()

        lineChart.invalidate()
    }

    private suspend fun calculateFft(): Map<Float, Float> = withContext(Dispatchers.IO) {
        val audioProcessor = EqualizerAudioProcessor(true)
        audioProcessor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        audioProcessor.flush()
        audioProcessor.preset = preset

        val size = 2.0.pow(14).toInt()

        val noise = Noise.real(size)

        var src = FloatArray(size)
        src[0] = 1f

        val gain = playbackPreferenceManager.preAmpGain
        val delta = gain.fromDb()

        src =
            src.map { value ->
                var newValue = value

                if (gain != 0.0) {
                    newValue = MathUtils.clamp((newValue * delta), Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toFloat()
                }

                for (band in audioProcessor.bandProcessors) {
                    newValue = band.processSample(newValue, 0)
                }
                newValue
            }.toFloatArray()

        val dst = FloatArray(size + 2)

        noise.fft(src, dst)

        (0 until size / 2).associateBy(
            { index -> ((index / (size / 2f)) * (44100 / 2f)) },
            { index -> (20f * log10(sqrt(((dst[index * 2]).pow(2)) + ((dst[index * 2 + 1]).pow(2))))) }
        )
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {
        const val TAG = "FrequencyResponseDialog"

        fun newInstance() = FrequencyResponseDialogFragment()
    }
}
