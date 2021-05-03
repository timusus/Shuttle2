package com.simplecityapps.shuttle.ui.screens.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.HorizontalSeekbar
import javax.inject.Inject


class DspFragment : Fragment(), Injectable, EqualizerContract.View {

    @Inject
    lateinit var presenter: DspPresenter

    private var toolbar: Toolbar by autoCleared()
    private var equalizerView: EqualizerView by autoCleared()
    private var eqPresetAutoComplete: AutoCompleteTextView by autoCleared()
    private var equalizerSwitch: SwitchCompat by autoCleared()
    private var frequencyResponseButton: ImageButton by autoCleared()
    private var replayGainAutoComplete: AutoCompleteTextView by autoCleared()
    private var preAmpSeekBar: HorizontalSeekbar by autoCleared()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dsp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        equalizerSwitch = view.findViewById(R.id.equalizerSwitch)
        equalizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            presenter.toggleEqualizer(isChecked)
        }

        frequencyResponseButton = view.findViewById(R.id.frequencyResponseButton)
        frequencyResponseButton.setOnClickListener {
            FrequencyResponseDialogFragment.newInstance().show(childFragmentManager)
        }

        equalizerView = view.findViewById(R.id.equalizerView)
        equalizerView.listener = equalizerListener

        val presets = Equalizer.Presets.all
        eqPresetAutoComplete = view.findViewById(R.id.eqPresetAutoComplete)
        eqPresetAutoComplete.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                presets.map { preset -> preset.name }
            )
        )
        eqPresetAutoComplete.setOnItemClickListener { _, _, position, _ ->
            presenter.setEqPreset(presets[position])
        }

        val modes = ReplayGainMode.values()
        replayGainAutoComplete = view.findViewById(R.id.replayGainAutoComplete)
        replayGainAutoComplete.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                modes.map { mode -> mode.displayName() }
            )
        )
        replayGainAutoComplete.setOnItemClickListener { _, _, position, _ ->
            presenter.setReplayGainMode(modes[position])
        }

        preAmpSeekBar = view.findViewById(R.id.preAmpSeekBar)
        preAmpSeekBar.listener = preAmpListener

        presenter.bindView(this)
    }


    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // Private

    private fun ReplayGainMode.displayName(): String {
        return when (this) {
            ReplayGainMode.Track -> getString(R.string.dsp_replay_gain_track)
            ReplayGainMode.Album -> getString(R.string.dsp_replay_gain_album)
            ReplayGainMode.Off -> getString(R.string.dsp_replay_gain_off)
        }
    }


    // EqualizerContract.View Implementation

    override fun initializeEqualizerView(activated: Boolean, equalizer: Equalizer.Presets.Preset, maxBandGain: Int) {
        equalizerSwitch.isChecked = activated

        equalizerView.configure(maxBandGain, equalizer)
        equalizerView.isActivated = activated

        eqPresetAutoComplete.setText(equalizer.name, false)
    }

    override fun updateEqualizerView(preset: Equalizer.Presets.Preset) {
        equalizerView.equalizer = preset
    }

    override fun updateSelectedEqPreset(preset: Equalizer.Presets.Preset) {
        eqPresetAutoComplete.setText(preset.name, false)
    }

    override fun showEqEnabled(enabled: Boolean) {
        equalizerView.isActivated = enabled
    }

    override fun updateSelectedReplayGainMode(mode: ReplayGainMode) {
        replayGainAutoComplete.setText(mode.displayName(), false)
    }

    override fun updatePreAmpGain(gain: Double) {
        val progress = ((gain + ReplayGainAudioProcessor.maxPreAmpGain) / (2 * ReplayGainAudioProcessor.maxPreAmpGain)).toFloat()
        preAmpSeekBar.progress = progress
    }


    // EqualizerView.Listener Implementation

    private val equalizerListener = object : EqualizerView.Listener {
        override fun onBandChanged(band: EqualizerBand) {
            presenter.updateBand(band)
        }
    }

    private val preAmpListener = object : HorizontalSeekbar.Listener {
        override fun onStopTracking(progress: Float) {
            val gain = (progress * ReplayGainAudioProcessor.maxPreAmpGain * 2) - ReplayGainAudioProcessor.maxPreAmpGain
            presenter.setPreAmpGain(gain.toDouble())
        }
    }

    // Static

    companion object {
        const val TAG = "EqualizerFragment"
        fun newInstance() = DspFragment()
    }
}