package com.simplecityapps.shuttle.ui.screens.trial

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallDialogFragment
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.EntitlementRepository
import com.simplecityapps.trial.MonetisationAnalytics
import com.simplecityapps.trial.PaywallSource
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrialDialogFragment : DialogFragment() {
    @Inject
    lateinit var entitlementRepository: EntitlementRepository

    @Inject
    lateinit var analytics: MonetisationAnalytics

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState == null) {
            analytics.paywallShown(PaywallSource.valueOf(requireArguments().getString(ARG_SOURCE)!!))
        }

        val view = layoutInflater.inflate(R.layout.dialog_trial, null)

        val icon: ImageView = view.findViewById(R.id.icon)

        var touchCount = 0
        var touchTime = 0L
        icon.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val now = System.currentTimeMillis()
                if (touchTime == 0L || now - touchTime > 2000) {
                    touchTime = now
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
            // Only consume if we're in an active tap sequence
            touchCount > 0 && System.currentTimeMillis() - touchTime <= 2000
        }

        val upgradeButton: Button = view.findViewById(R.id.upgradeButton)
        upgradeButton.setOnClickListener {
            dismiss()
            PaywallDialogFragment.show(parentFragmentManager, PaywallSource.valueOf(requireArguments().getString(ARG_SOURCE)!!))
        }

        val heading: TextView = view.findViewById(R.id.heading)
        val subheading: TextView = view.findViewById(R.id.subheading)
        val description: TextView = view.findViewById(R.id.description)

        lifecycleScope.launch {
            entitlementRepository.entitlement.collect { entitlement ->
                description.text = getString(R.string.trial_description_pro)
                when (entitlement) {
                    is Entitlement.Trial -> {
                        val daysRemaining = entitlement.daysRemaining()
                        heading.text = getString(R.string.trial_heading_trial)
                        subheading.text = Phrase.fromPlural(requireContext(), R.plurals.trial_days_remaining, daysRemaining).put("count", daysRemaining).format()
                        subheading.isVisible = true
                    }

                    is Entitlement.Free -> {
                        heading.text = getString(if (entitlement.trialUsed) R.string.trial_heading_expired else R.string.trial_heading_pro)
                        subheading.isVisible = false
                    }

                    is Entitlement.Pro -> {
                        dismissAllowingStateLoss()
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

        private const val ARG_SOURCE = "source"

        fun newInstance(source: PaywallSource): TrialDialogFragment = TrialDialogFragment().withArgs {
            putString(ARG_SOURCE, source.name)
        }
    }
}
