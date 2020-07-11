package com.simplecityapps.shuttle.ui.screens.equalizer

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.paramsen.noise.Noise
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.fromDb
import com.simplecityapps.playback.local.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class FrequencyResponseDialogFragment : DialogFragment(), Injectable {

    private var lineChart: LineChart by autoCleared()
    private var loadingView: CircularLoadingView by autoCleared()

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    private lateinit var preset: Equalizer.Presets.Preset

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var textColor: Int = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        preset = playbackPreferenceManager.preset
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TypedValue().apply {
            requireContext().theme.resolveAttribute(android.R.attr.textColorSecondary, this, true)
            textColor = ContextCompat.getColor(requireContext(), resourceId)
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = View.inflate(requireContext(), R.layout.fragment_frequency_response_dialog, null)

        lineChart = view.findViewById(R.id.lineChart)

        loadingView = view.findViewById(R.id.loadingView)
        loadingView.setState(CircularLoadingView.State.Loading())

        coroutineScope.launch {
            val fft = calculateFft()
            setData(fft)
            loadingView.setState(CircularLoadingView.State.None)
            lineChart.isVisible = true
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Frequency Response")
            .setView(view)
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        coroutineScope.cancel()
        super.onDestroyView()
    }

    fun setData(data: Map<Float, Float>) {

        val entries = data.map { Entry(log10(it.key), it.value) }

        val dataset = LineDataSet(entries, "Frequency Response")
        dataset.mode = LineDataSet.Mode.HORIZONTAL_BEZIER
        dataset.lineWidth = 1f
        dataset.setDrawCircles(false)
        dataset.setDrawHorizontalHighlightIndicator(false)
        dataset.setDrawVerticalHighlightIndicator(false)
        dataset.color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)

        val chartData = LineData(dataset)
        chartData.setDrawValues(false)
        lineChart.data = chartData

        lineChart.axisLeft.textColor = textColor
        lineChart.axisLeft.axisMinimum = -20f
        lineChart.axisLeft.axisMaximum = 20f
        lineChart.axisLeft.labelCount = 7
        lineChart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "%.1f".format(value) + "dB"
            }
        }
        lineChart.axisRight.isEnabled = false

        lineChart.xAxis.textColor = textColor
        lineChart.xAxis.axisMinimum = 1f
        lineChart.xAxis.axisMaximum = log10(20500f)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.setDrawAxisLine(false)
        lineChart.xAxis.setLabelCount(8, true)
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
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

    private suspend fun calculateFft(): Map<Float, Float> {
        return withContext(Dispatchers.IO) {
            val audioProcessor = EqualizerAudioProcessor(true)
            audioProcessor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
            audioProcessor.flush()
            audioProcessor.preset = preset

            val size = 2.0.pow(14).toInt()

            val noise = Noise.real(size)

            val src = FloatArray(size)
            src[0] = 1f

            src.forEachIndexed { index, value ->
                var newValue = value

                // Adjust overall gain to prevent clipping (create headroom). Used when max gain is above 3dB
                val maxBandGain = max(0, (preset.bands.map { band -> band.gain }.max() ?: 0))
                if (maxBandGain > 3) {
                    newValue = (newValue * ((-maxBandGain).fromDb())).toFloat()
                }

                for (band in audioProcessor.bandProcessors) {
                    newValue = band.processSample(newValue, 0)
                }
                src[index] = newValue
            }

            val dst = FloatArray(size + 2)

            noise.fft(src, dst)

            (0 until size / 2).associateBy(
                { index -> ((index / (size / 2f)) * (44100 / 2f)) },
                { index -> (20f * log10(sqrt(((dst[index * 2]).pow(2)) + ((dst[index * 2 + 1]).pow(2))))) })
        }
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {
        const val TAG = "FrequencyResponseDialog"

        fun newInstance() = FrequencyResponseDialogFragment()
    }
}