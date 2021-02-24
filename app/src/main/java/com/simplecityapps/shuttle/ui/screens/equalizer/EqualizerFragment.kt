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
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerBand
import com.simplecityapps.playback.exoplayer.ReplayGainAudioProcessor
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import javax.inject.Inject


class EqualizerFragment : Fragment(), Injectable, EqualizerContract.View {

    @Inject
    lateinit var presenter: EqualizerPresenter

    private var toolbar: Toolbar by autoCleared()

    private var equalizerView: EqualizerView by autoCleared()

    private var eqPresetAutoComplete: AutoCompleteTextView by autoCleared()

    private var equalizerSwitch: SwitchCompat by autoCleared()

    private var frequencyResponseButton: ImageButton by autoCleared()

    private var replayGainAutoComplete: AutoCompleteTextView by autoCleared()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
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

        val modes = ReplayGainAudioProcessor.Mode.values()
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

        presenter.bindView(this)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // Private

    private fun ReplayGainAudioProcessor.Mode.displayName(): String {
        return when (this) {
            ReplayGainAudioProcessor.Mode.Track -> "Track Gain"
            ReplayGainAudioProcessor.Mode.Album -> "Album Gain"
            ReplayGainAudioProcessor.Mode.Off -> "Off"
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

    override fun updateSelectedReplayGainMode(mode: ReplayGainAudioProcessor.Mode) {
        replayGainAutoComplete.setText(mode.displayName(), false)
    }


    // EqualizerView.Listener Implementation

    private val equalizerListener = object : EqualizerView.Listener {

        override fun onBandChanged(band: EqualizerBand) {
            presenter.updateBand(band)
        }
    }


    // Static

    companion object {
        const val TAG = "EqualizerFragment"
        fun newInstance() = EqualizerFragment()
    }
}