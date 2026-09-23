package com.simplecityapps.shuttle.ui.screens.equalizer

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.playback.dsp.equalizer.BandProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.frequencyResponseDb
import com.simplecityapps.playback.dsp.equalizer.toNyquistBand
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.pow
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nominal sample rate the response is evaluated at - the analytical magnitude response is independent of it, provided every band's centre frequency stays below Nyquist. */
private const val ANALYSIS_SAMPLE_RATE = 44100

/** Number of log-spaced points plotted between 20 Hz and 20.5 kHz - enough to draw a smooth curve. */
private const val ANALYSIS_POINT_COUNT = 300
private const val ANALYSIS_MIN_FREQUENCY = 20.0
private const val ANALYSIS_MAX_FREQUENCY = 20_500.0

@AndroidEntryPoint
class FrequencyResponseDialogFragment : DialogFragment() {
    private var composeView: ComposeView by autoCleared()
    private var loadingView: CircularLoadingView by autoCleared()

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject
    lateinit var generalPreferenceManager: GeneralPreferenceManager

    private lateinit var preset: Equalizer.Presets.Preset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preset = playbackPreferenceManager.preset
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = View.inflate(requireContext(), R.layout.fragment_frequency_response_dialog, null)

        composeView = view.findViewById(R.id.composeView)

        loadingView = view.findViewById(R.id.loadingView)
        loadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))

        lifecycleScope.launch {
            val points = withContext(Dispatchers.Default) { calculateFrequencyResponse() }

            composeView.setContent {
                val theme by generalPreferenceManager.theme(lifecycleScope).collectAsStateWithLifecycle()
                val accent by generalPreferenceManager.accent(lifecycleScope).collectAsStateWithLifecycle()

                AppTheme(theme = theme, accent = accent) {
                    FrequencyResponseChart(
                        points = points.toImmutableList(),
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    )
                }
            }

            loadingView.setState(CircularLoadingView.State.None)
            composeView.isVisible = true
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dsp_dialog_frequency_response_title))
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .show()
    }

    /**
     * Evaluates the preset's frequency response analytically at log-spaced points, rather than
     * measuring an FFT of an impulse response: exact rather than approximate, and it needs neither
     * an FFT library nor a real [androidx.media3.common.audio.AudioProcessor].
     */
    private fun calculateFrequencyResponse(): List<FrequencyResponsePoint> {
        val bandProcessors = preset.bands.map { band ->
            BandProcessor(band.toNyquistBand(), sampleRate = ANALYSIS_SAMPLE_RATE, channelCount = 1, referenceGain = 0.0)
        }
        val preAmpGainDb = playbackPreferenceManager.preAmpGain

        val logSpan = ANALYSIS_MAX_FREQUENCY / ANALYSIS_MIN_FREQUENCY
        return (0 until ANALYSIS_POINT_COUNT).map { index ->
            val frequency = ANALYSIS_MIN_FREQUENCY * logSpan.pow(index.toDouble() / (ANALYSIS_POINT_COUNT - 1))
            val gainDb = frequencyResponseDb(bandProcessors, preAmpGainDb, frequency, ANALYSIS_SAMPLE_RATE)
            FrequencyResponsePoint(frequency.toFloat(), gainDb.toFloat())
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
