package com.simplecityapps.shuttle.ui.screens.onboarding.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.remote_config.AnalyticsManager
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class AnalyticsPermissionFragment :
    Fragment(),
    OnboardingChild {
    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_onboarding_privacy, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val crashReportingSwitch: SwitchCompat = view.findViewById(R.id.crashReportingSwitch)
        crashReportingSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            preferenceManager.crashReportingEnabled = isChecked
        }
        val firebaseAnalyticsSwitch: SwitchCompat = view.findViewById(R.id.analyticsSwitch)
        firebaseAnalyticsSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            preferenceManager.firebaseAnalyticsEnabled = isChecked
            analyticsManager.enableAnalytics(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()

        preferenceManager.hasSeenOnboardingAnalyticsDialog = true

        // It seems we need some sort of arbitrary delay, to ensure the parent fragment has indeed finished its onViewCreated() and instantiated the next button.
        view?.postDelayed({
            getParent()?.let { parent ->
                parent.hideBackButton()
                parent.toggleNextButton(true)
                parent.showNextButton(getString(R.string.onboarding_button_next))
            } ?: Timber.e("Failed to update state - getParent() returned null")
        }, 50)
    }

    // OnboardingChild Implementation

    override val page = OnboardingPage.AnalyticsPermission

    override fun getParent() = parentFragment as? OnboardingParent

    override fun handleNextButtonClick() {
        getParent()?.goToNext()
    }
}
