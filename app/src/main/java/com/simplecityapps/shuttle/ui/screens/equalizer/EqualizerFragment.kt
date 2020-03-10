package com.simplecityapps.shuttle.ui.screens.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerBand
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import javax.inject.Inject


class EqualizerFragment : Fragment(), Injectable, EqualizerContract.View {

    @Inject lateinit var presenter: EqualizerPresenter

    private var toolbar: Toolbar by autoCleared()

    private var equalizerView: EqualizerView by autoCleared()

    private var autoCompleteTextView: AutoCompleteTextView by autoCleared()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.inflateMenu(R.menu.menu_fragment_equalizer)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        (toolbar.menu.findItem(R.id.enableEqualizer).actionView as SwitchCompat).setOnClickListener { switch ->
            presenter.toggleEqualizer((switch as SwitchCompat).isChecked)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.frequencyResponse -> {
                    FrequencyResponseDialogFragment.newInstance().show(childFragmentManager)
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }

        equalizerView = view.findViewById(R.id.equalizerView)
        equalizerView.listener = equalizerListener

        val presets = Equalizer.Presets.all
        autoCompleteTextView = view.findViewById(R.id.autoCompleteTextView)
        autoCompleteTextView.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_menu_popup_item,
                presets.map { preset -> preset.name }
            )
        )
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            presenter.setPreset(presets[position])
        }

        presenter.bindView(this)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // EqualizerContract.View Implementation

    override fun initializeEqualizerView(activated: Boolean, equalizer: Equalizer.Presets.Preset, maxBandGain: Int) {
        (toolbar.menu.findItem(R.id.enableEqualizer).actionView as SwitchCompat).isChecked = activated

        equalizerView.configure(maxBandGain, equalizer)
        equalizerView.isActivated = activated

        autoCompleteTextView.setText(equalizer.name, false)
    }

    override fun updateEqualizerView(preset: Equalizer.Presets.Preset) {
        equalizerView.equalizer = preset
    }

    override fun updateSelectedPreset(preset: Equalizer.Presets.Preset) {
        autoCompleteTextView.setText(preset.name, false)
    }

    override fun showEnabled(enabled: Boolean) {
        equalizerView.isActivated = enabled
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