package com.simplecityapps.shuttle.ui.screens.trial

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrialDialogFragment : DialogFragment() {

    @Inject
    lateinit var trialManager: TrialManager

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        preferenceManager.lastViewedTrialDialog = Date()

        val view = layoutInflater.inflate(R.layout.dialog_trial, null)

        val icon: ImageView = view.findViewById(R.id.icon)

        var touchCount = 0
        var touchTime = 0L
        icon.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (touchTime == 0L || System.currentTimeMillis() - touchTime > 2000) {
                    touchTime = System.currentTimeMillis()
                    touchCount = 1
                } else {
                    touchCount++
                }
                if (touchCount == 5) {
                    EditTextAlertDialog.newInstance(
                        title = "Promo code",
                        hint = "Email Address",
                        inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    ).show(parentFragmentManager)
                    dismiss()
                }
            }
            true
        }

        val upgradeButton: Button = view.findViewById(R.id.upgradeButton)
        upgradeButton.setOnClickListener {
            dismiss()
            PurchaseDialogFragment.newInstance().show(parentFragmentManager)
        }

        val heading: TextView = view.findViewById(R.id.heading)
        val subheading: TextView = view.findViewById(R.id.subheading)
        val description: TextView = view.findViewById(R.id.description)

        lifecycleScope.launch {
            trialManager.trialState.collect { trialState ->
                when (trialState) {
                    is TrialState.Trial -> {
                        val daysRemaining = TimeUnit.MILLISECONDS.toDays(trialState.timeRemaining).toInt()
                        heading.text = getString(R.string.trial_heading_trial)
                        subheading.text = Phrase.fromPlural(requireContext(), R.plurals.trial_days_remaining, daysRemaining).put("count", daysRemaining).format()
                        description.text = getString(R.string.trial_description_trial)
                    }
                    is TrialState.Expired -> {
                        heading.text = getString(R.string.trial_heading_expired)
                        subheading.text = Phrase.from(requireContext(), R.string.trial_playback_speed).put("speed", String.format("%.1fx", trialState.multiplier())).format()
                        description.text = getString(R.string.trial_description_expired)
                    }
                }
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }


    companion object {
        private const val TAG = "TrialDialogFragment"

        fun newInstance(): TrialDialogFragment = TrialDialogFragment()
    }
}